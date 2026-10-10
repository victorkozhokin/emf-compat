package strm.touchnmotion;

import strm.touchnmotion.interaction.HandContacts;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.touchnmotion.footgrounding.compat.FootGrounding;
import strm.touchnmotion.footgrounding.compat.HorseFootGrounding;
import strm.touchnmotion.interaction.InteractionRuntime;
import strm.touchnmotion.buttonpress.ButtonPress;
import strm.touchnmotion.blockuse.BlockUse;
import strm.touchnmotion.create.ejector.EjectorLaunch;
import strm.touchnmotion.mining.Mining;
import strm.touchnmotion.motion.PoseInertia;
import strm.touchnmotion.torso.TorsoLean;
import strm.touchnmotion.wallhand.WallSqueeze;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseSnapshot;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFBipedPose;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.animation.state.EMFState;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Puts every feature's change on top of whatever the model was animated to. EMF calls
 * {@code onAnimationEnd} once per entity render, after the pack animation and after the core has
 * put captured poses back (the core's hook is registered first), so walking, crouching and other
 * addons' poses all stay and only get the additions blended over them.
 *
 * <p>The skin's outer layer - sleeves, trouser legs, the hat - are parts of their own beside the
 * limbs they cover, on the same pivots, so each gets its limb's transform copied after the change.
 * Armour is drawn by a separate model that EMF copies the animated pose onto
 * ({@code onBipedPoseCopyEnd}), without what the hooks did; the same changes are made there too, as
 * the core does with its captured poses.</p>
 */
public final class TouchNMotionHook extends EMFAnimationApi.EMFAnimationHook {

    private static final Logger LOGGER = LoggerFactory.getLogger("TouchNMotion");

    private static boolean failureLogged, armourFailureLogged;

    /** Each outer-layer part and the limb it covers. */
    private static final String[][] LAYERS = {
            {"hat", "head"}, {"jacket", "body"}, {"right_sleeve", "right_arm"}, {"left_sleeve", "left_arm"},
            {"right_pants", "right_leg"}, {"left_pants", "left_leg"}};

    private TouchNMotionHook() {
    }

    public static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new TouchNMotionHook());
        } catch (Throwable t) {
            LOGGER.warn("[TouchNMotion] could not register the EMF animation hook", t);
        }
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        try {
            EMFEntityRenderState state = context.activeState();
            if (state == null || state.isFirstPersonHand()) return;
            UUID uuid = state.uuid();
            if (uuid == null) return;
            // EMF runs this for every creature it animates; ours are the players and the horses.
            boolean player = state.emfEntity() instanceof Player;
            if (!player && !HorseFootGrounding.handles(state.emfEntity())) return;
            if (state.emfEntity() instanceof net.minecraft.world.entity.Entity drawn && !strm.touchnmotion.interaction.DrawnEntities.inWorld(drawn)) return;
            Map<String, EMFModelPartVanilla> parts = context.animatingModelRoot().getAllVanillaPartsByNameEMF();
            if (!player) {
                // A horse: its hooves, and nothing of what a player gets.
                HorseFootGrounding.apply(uuid, parts::get);
                return;
            }
            if (context.animatingModelRoot().isMainModel) FootGrounding.recordAnimated(uuid, parts::get);
            applyAll(uuid, parts::get, player, context.animatingModelRoot().isMainModel);
            //? if >=1.21.11 {
            /*if (context.animatingModelRoot().isMainModel) Drawn.keep(uuid, parts::get);
            *///?}
            // From 1.21.11 on the outer layer is a child of its limb and moves with it.
            //? if <1.21.11 {
            for (String[] layer : LAYERS) {
                ModelPart outer = parts.get(layer[0]);
                ModelPart limb = parts.get(layer[1]);
                if (outer != null && limb != null) PoseSnapshot.copy(limb, outer);
            }
            //?}
        } catch (Throwable t) {
            // A throw out of an animation hook makes EMF disable the model's animations for good.
            // Report once: silently swallowing this made visual tests exercise only the fallback.
            if (!failureLogged) {
                failureLogged = true;
                LOGGER.warn("[TouchNMotion] failed to apply additive pose", t);
            }
        }
    }

    @Override
    public void onBipedPoseCopyEnd(EMFBipedPose pose, HumanoidModel<?> model, boolean wasCancelledByHook) {
        try {
            // The biped hook carries no state of its own; this copy belongs to the entity being
            // rendered right now.
            EMFEntityRenderState state = EMFState.state();
            if (state == null) return;
            UUID uuid = state.uuid();
            if (uuid == null || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
            // Armour is only a player's concern here: nothing of ours is on any other biped.
            if (!(state.emfEntity() instanceof Player drawn) || !strm.touchnmotion.interaction.DrawnEntities.inWorld(drawn)) return;
            //? if <1.21.11 {
            applyAll(uuid, name -> switch (name) {
                case "head" -> model.head;
                case "hat" -> model.hat;
                case "body" -> model.body;
                case "right_arm" -> model.rightArm;
                case "left_arm" -> model.leftArm;
                case "right_leg" -> model.rightLeg;
                case "left_leg" -> model.leftLeg;
                default -> null;
            }, true, false);
            model.hat.copyFrom(model.head);
            //?} else {
            /*// From 1.21.11 on the pose EMF copies here already has what the hook did to the body
            // drawn, so doing it again would do it twice: the copy gets the drawn pose itself.
            Drawn.put(uuid, model);
            *///?}
        } catch (Throwable t) {
            // Same as above: never throw out of an EMF hook, and say so once.
            if (!armourFailureLogged) {
                armourFailureLogged = true;
                LOGGER.warn("[TouchNMotion] failed to apply additive pose to the armour", t);
            }
        }
    }

    /**
     * From 1.21.11 on: the six parts of a player's model as they are drawn this frame, kept for the
     * models that are drawn over it - armour, a cape's or a mod's copy of the body.
     */
    public static final class Drawn {
        private static final String[] NAMES = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
        private static final Map<UUID, PoseSnapshot[]> BY_PLAYER = new java.util.HashMap<>();
        private static final Map<UUID, org.joml.Matrix4f> ROOTS = new java.util.HashMap<>();

        private Drawn() {
        }

        static void keep(UUID uuid, Function<String, ModelPart> parts) {
            PoseSnapshot[] kept = new PoseSnapshot[NAMES.length];
            for (int i = 0; i < NAMES.length; i++) {
                ModelPart part = parts.apply(NAMES[i]);
                kept[i] = part == null ? null : new PoseSnapshot(part);
            }
            BY_PLAYER.put(uuid, kept);
            // The root every part hangs off, as a part applies itself: moved, turned (Z, Y, X), scaled.
            ModelPart root = parts.apply("root");
            if (root == null) {
                ROOTS.remove(uuid);
            } else {
                ROOTS.put(uuid, strm.touchnmotion.interaction.PartMath.transform(root.x, root.y, root.z,
                        root.xRot, root.yRot, root.zRot, root.xScale, root.yScale, root.zScale));
            }
        }

        /** The root's pose the player's model was last drawn with, as a transform in the model's space; {@code null} for a model with no root, or not drawn yet. */
        public static org.joml.Matrix4f root(UUID uuid) {
            return ROOTS.get(uuid);
        }

        static void put(UUID uuid, HumanoidModel<?> model) {
            PoseSnapshot[] kept = BY_PLAYER.get(uuid);
            if (kept == null) return;
            ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
            for (int i = 0; i < parts.length; i++) if (kept[i] != null) kept[i].apply(parts[i]);
        }

        /** Leaving a world. */
        public static void clear() {
            BY_PLAYER.clear();
            ROOTS.clear();
        }
    }

    private static void applyAll(UUID uuid, Function<String, ModelPart> parts, boolean player, boolean mainModel) {
        // A hat that hangs off the head (1.21.11 on) moves with it: to the features, which move a hat
        // that is a part of its own beside the head, it is not there.
        ModelPart headPart = parts.apply("head");
        if (headPart != null && headPart.hasChild("hat")) {
            Function<String, ModelPart> all = parts;
            parts = name -> "hat".equals(name) ? null : all.apply(name);
        }
        var entity = EMFState.state();
        net.minecraft.client.player.AbstractClientPlayer client = player && entity != null
                && entity.emfEntity() instanceof net.minecraft.client.player.AbstractClientPlayer p ? p : null;
        if (client != null && (strm.touchnmotion.compat.ParCoolActivity.active(client)
                || strm.touchnmotion.compat.WholeBody.held(client))) return;
        if (client != null && strm.touchnmotion.compat.WholeBody.seated(client)) {
            strm.touchnmotion.lookat.LookAt.apply(uuid, parts);
            return;
        }
        float[] before = client != null && mainModel ? strm.touchnmotion.compat.ParCoolActivity.before(parts) : null;
        applyAllParts(uuid, parts, player, mainModel);
        if (before != null) strm.touchnmotion.compat.ParCoolActivity.after(client, parts, before);
    }

    private static void applyAllParts(UUID uuid, Function<String, ModelPart> parts, boolean player, boolean mainModel) {
        // First, on the pack's own pose: its cuts settle before anything corrects it. A player's only:
        // the hook runs for every creature EMF animates.
        if (player) PoseInertia.apply(uuid, parts, EMFState.getFrameCounter());
        FootGrounding.apply(uuid, parts);
        strm.touchnmotion.ride.MinecartRide.legs(uuid, parts);
        strm.touchnmotion.ride.BoatRide.seat(uuid, parts);
        strm.touchnmotion.ride.BoatPassenger.legs(uuid, parts);
        EjectorLaunch.apply(uuid, parts);
        WallSqueeze.apply(uuid, parts);
        var supportBase = InteractionRuntime.beginSupport(uuid, parts);
        HandContacts.support(uuid, parts);
        ButtonPress.apply(uuid, parts);
        BlockUse.apply(uuid, parts);
        strm.touchnmotion.lead.LeadHold.support(uuid, parts);
        strm.touchnmotion.pocket.PocketStash.support(uuid, parts);
        strm.touchnmotion.wallhand.FenceLean.support(uuid, parts);
        strm.touchnmotion.fishing.Fishing.support(uuid, parts);
        strm.touchnmotion.lookat.LookAt.support(uuid, parts);
        strm.touchnmotion.fright.Fright.support(uuid, parts);
        Mining.support(uuid, parts);
        strm.touchnmotion.gesture.Gesture.support(uuid, parts);
        strm.touchnmotion.compat.CombatBody.support(uuid, parts);
        // The torso before the arm aims: a hand on a wall aims from where the shoulder has gone.
        WallSqueeze.support(uuid, parts);
        strm.touchnmotion.transport.TransportGrip.support(uuid, parts);
        float[] attackArms = strm.touchnmotion.compat.CombatBody.armsBefore(uuid, parts);
        TorsoLean.apply(uuid, parts);
        strm.touchnmotion.compat.CombatBody.armsAfter(parts, attackArms);
        strm.touchnmotion.transport.TransportGrip.reach(uuid, parts);
        HandContacts.reach(uuid, parts);
        BlockUse.reachContact(uuid, parts);
        Mining.reach(uuid, parts);
        strm.touchnmotion.gesture.Gesture.reach(uuid, parts);
        ButtonPress.reachContact(uuid, parts);
        strm.touchnmotion.buttonpress.aeronautics.HeavyThrottle.reachContact(uuid, parts);
        strm.touchnmotion.fishing.Fishing.reach(uuid, parts);
        InteractionRuntime.finishSupport(uuid, parts, supportBase, EMFState.getFrameCounter(), mainModel);
        var contactBase = InteractionRuntime.beginHands(uuid, parts);
        strm.touchnmotion.lookat.LookAt.apply(uuid, parts);
        InteractionRuntime.apply(uuid, parts);
        // The hands on the walls of a narrow gap, from where the turned torso has put the shoulders.
        WallSqueeze.aimArms(uuid, parts);
        // Last: a hand on a button or a swing on a block aims from where its shoulder has finally been drawn.
        ButtonPress.aimArm(uuid, parts, contactBase);
        BlockUse.aimArm(uuid, parts);
        Mining.aimArm(uuid, parts, contactBase);
        strm.touchnmotion.gesture.Gesture.aimArms(uuid, parts, contactBase);
        strm.touchnmotion.pocket.PocketStash.aimArm(uuid, parts);
        strm.touchnmotion.blockuse.aeronautics.CockpitControls.apply(uuid, parts);
        HandContacts.apply(uuid, parts, contactBase, mainModel);
        strm.touchnmotion.buttonpress.aeronautics.HeavyThrottle.aimArms(uuid, parts);
        strm.touchnmotion.transport.TransportGrip.aim(uuid, parts);
        strm.touchnmotion.lead.LeadHold.capture(uuid, parts);
        strm.touchnmotion.ride.BoatRide.grip(uuid, parts);
        strm.touchnmotion.ride.BoatPassenger.grip(uuid, parts);
        strm.touchnmotion.ride.MinecartRide.grip(uuid, parts);
        strm.touchnmotion.wallhand.FenceLean.grip(uuid, parts);
        strm.touchnmotion.fishing.Fishing.grip(uuid, parts);
        strm.touchnmotion.fright.Fright.apply(uuid, parts);
        InteractionRuntime.finishHands(uuid, parts, contactBase, EMFState.getFrameCounter(), mainModel);
    }
}
