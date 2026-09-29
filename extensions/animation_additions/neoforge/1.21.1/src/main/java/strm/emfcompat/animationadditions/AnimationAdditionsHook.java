package strm.emfcompat.animationadditions;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.animationadditions.footgrounding.compat.FootGrounding;
import strm.emfcompat.animationadditions.footgrounding.compat.HorseFootGrounding;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.buttonpress.ButtonPress;
import strm.emfcompat.animationadditions.blockuse.BlockUse;
import strm.emfcompat.animationadditions.mining.Mining;
import strm.emfcompat.animationadditions.motion.PoseInertia;
import strm.emfcompat.animationadditions.torso.TorsoLean;
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
public final class AnimationAdditionsHook extends EMFAnimationApi.EMFAnimationHook {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatAnimationAdditions");

    /** Each outer-layer part and the limb it covers. */
    private static final String[][] LAYERS = {
            {"hat", "head"}, {"jacket", "body"}, {"right_sleeve", "right_arm"}, {"left_sleeve", "left_arm"},
            {"right_pants", "right_leg"}, {"left_pants", "left_leg"}};

    private AnimationAdditionsHook() {
    }

    public static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new AnimationAdditionsHook());
        } catch (Throwable t) {
            LOGGER.warn("[AnimationAdditions] could not register the EMF animation hook", t);
        }
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        try {
            EMFEntityRenderState state = context.activeState();
            if (state == null || state.isFirstPersonHand()) return;
            UUID uuid = state.uuid();
            if (uuid == null) return;
            Map<String, EMFModelPartVanilla> parts = context.animatingModelRoot().getAllVanillaPartsByNameEMF();
            if (context.animatingModelRoot().isMainModel) FootGrounding.recordAnimated(uuid, parts::get);
            applyAll(uuid, parts::get);
            for (String[] layer : LAYERS) {
                ModelPart outer = parts.get(layer[0]);
                ModelPart limb = parts.get(layer[1]);
                if (outer != null && limb != null) PoseSnapshot.copy(limb, outer);
            }
        } catch (Throwable t) {
            // A throw out of an animation hook makes EMF disable the model's animations for good.
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
            applyAll(uuid, name -> switch (name) {
                case "head" -> model.head;
                case "hat" -> model.hat;
                case "body" -> model.body;
                case "right_arm" -> model.rightArm;
                case "left_arm" -> model.leftArm;
                case "right_leg" -> model.rightLeg;
                case "left_leg" -> model.leftLeg;
                default -> null;
            });
            model.hat.copyFrom(model.head);
        } catch (Throwable t) {
            // Same as above: never throw out of an EMF hook.
        }
    }

    private static void applyAll(UUID uuid, Function<String, ModelPart> parts) {
        // First, on the pack's own pose: its cuts settle before anything corrects it.
        PoseInertia.apply(uuid, parts, EMFState.getFrameCounter());
        FootGrounding.apply(uuid, parts);
        ButtonPress.apply(uuid, parts);
        BlockUse.apply(uuid, parts);
        HorseFootGrounding.apply(uuid, parts);
        // The torso before the arm aims: a hand on a wall aims from where the shoulder has gone.
        TorsoLean.apply(uuid, parts);
        InteractionRuntime.apply(uuid, parts);
        // Last: a hand on a button or a swing on a block aims from where its shoulder has finally been drawn.
        ButtonPress.aimArm(uuid, parts);
        BlockUse.aimArm(uuid, parts);
        Mining.aimArm(uuid, parts);
    }
}
