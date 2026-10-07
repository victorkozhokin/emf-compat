package strm.emfcompat.hackersandslashers.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.hackersandslashers.EMFCompatHnSMod;
import strm.emfcompat.hackersandslashers.compat.HnSCompat;
import strm.emfcompat.hackersandslashers.compat.HnSHeadLook;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Captures the pose Hackers 'n Slashers has put on the arms, so the core can restore it after EMF
 * has animated the model from the resource pack.
 *
 * <p>Note which {@code setupAnim} this targets. {@code PlayerModel} has two: the real
 * {@code setupAnim(LivingEntity, ...)} it inherits from {@code HumanoidModel}, and the synthetic
 * bridge {@code setupAnim(Entity, ...)} that {@code EntityModel} declares and the renderer
 * actually calls. zigythebird's animation library — the one Hackers 'n Slashers drives — applies
 * its result at RETURN of the <em>bridge</em>, which runs after the real method has already
 * returned. Capturing at the real method's RETURN, the way the Better Combat addon does, therefore
 * reads the model before any of this mod's animation has been applied, and pins whatever the pose
 * was beforehand. Better Combat gets away with it because kosmx's library, which it uses, injects
 * into the real method instead.</p>
 *
 * <p>So this targets the bridge, at priority 2500 against the library's 2001 so it runs after it
 * at the same instruction. The descriptor is spelled out rather than left as a bare name, so the
 * selector can only mean the bridge and never the real method.</p>
 *
 * <p>The pose capture remains third-person only. First person is handled separately by the EMF
 * vanilla-model condition registered by {@link EMFCompatHnSMod}: it exposes H&amp;S' own
 * first-person animation and item transforms instead of copying the third-person snapshot.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
@SuppressWarnings("unchecked")
public class PlayerModelMixin {

    @Unique
    private static final String SOURCE = EMFCompatHnSMod.SOURCE;

    @Unique
    private static final String POSE_SOURCE = EMFCompatHnSMod.POSE_SOURCE;

    /**
     * When the legs are held: the player counts as standing still below {@link #LEGS_HOLD_BELOW}
     * blocks per tick of horizontal movement and as moving above {@link #LEGS_FREE_ABOVE}, and
     * between the two keeps whatever it was.
     *
     * <p>This used to read {@code limbSwingAmount < 0.15}, which is the wrong question: it is the
     * smoothed amplitude of the stride, not speed, and H&amp;S slows the player during a swing, so
     * it wandered between 0.06 and 0.23 while sneaking forward and across 0.15 on every stride -
     * the legs flipped between the stance and the pack's walk cycle, and any threshold high enough
     * to stop that held the legs of a player who was walking. Measured speed has no such overlap:
     * exactly 0 standing (with or without a swing, crouched or not), 0.011-0.061 sneaking forward
     * through swings, 0.037 and up walking.</p>
     */
    @Unique
    private static final double LEGS_HOLD_BELOW = 0.005;

    @Unique
    private static final double LEGS_FREE_ABOVE = 0.010;

    /** A switch has to be wanted this long first, so a one-frame spike does not flip the legs. */
    @Unique
    private static final long LEGS_SWITCH_NANOS = 100_000_000L;

    /** Per player during an action: {legs held 1/0, since when a switch is wanted or 0}. */
    @Unique
    private static final Map<UUID, long[]> LEG_GATES = new HashMap<>();

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", at = @At("RETURN"))
    private void emfcompat$captureHnSPose(Entity entity, float limbSwing, float limbSwingAmount,
                                          float ageInTicks, float netHeadYaw, float headPitch,
                                          CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        UUID uuid = player.getUUID();

        if (!EMFCompatHnSMod.isEnabled()
                || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)
                || (EMFCompatHnSMod.isLocalPlayerInFirstPerson(player)
                    && HnSCompat.isFirstPersonAnimationActive(player))) {
            PoseManager.clearPoses(uuid, SOURCE);
            PoseManager.clearPoses(uuid, POSE_SOURCE);
            return;
        }

        PlayerModel<AbstractClientPlayer> model = (PlayerModel<AbstractClientPlayer>) (Object) this;

        // Body-follow: arm poses keep their shape and follow the torso (bodyBase = where the body
        // was at capture). Rotation-only (legacy): no bodyBase, so the arms keep only rotation.
        Vector3f bodyBase = EMFCompatHnSMod.isBodyFollow()
                ? new Vector3f(model.body.x, model.body.y, model.body.z)
                : null;

        boolean action = HnSCompat.isActionActive(player);
        boolean stance = EMFCompatHnSMod.isStances() && HnSCompat.isStanceActive(player);
        PoseSnapshot[] arms = emfcompat$arms(model, uuid, action, stance && action && HnSCompat.isReturning(player), stance);

        if (stance) {
            // Arms only: the player walks around in this pose, so the legs have to keep EMF's cycle.
            PoseManager.savePoses(uuid, POSE_SOURCE, arms[0], arms[1], null, bodyBase);
        } else {
            PoseManager.clearPoses(uuid, POSE_SOURCE);
        }

        if (!action) {
            PoseManager.clearPoses(uuid, SOURCE);
            // Each action decides afresh: a gate left from the last swing would hold the legs of
            // a player who has started running since.
            LEG_GATES.remove(uuid);
            return;
        }

        // Hold the legs as well, so a lunge or a roll keeps its stance instead of walking through
        // it. Two safeguards, both borrowed from the Better Combat addon: rotation-only, which
        // keeps the legs pivoted at the hip rather than detaching them, and only while roughly
        // stationary, so a moving player keeps EMF's walk cycle.
        Map<String, PoseSnapshot> parts = null;
        boolean planted = emfcompat$legsHeld(uuid, player);
        // The experiment: Touch'n Motion, if it is there, turns the torso with the attack and
        // sets the feet itself - then the legs are its to place, not the animation's to hold.
        boolean procedural = EMFCompatHnSMod.isProceduralBody()
                && strm.emfcompat.hackersandslashers.compat.BodyBridge.offer(uuid, model.body.xRot, model.body.yRot, model.body.zRot, planted);
        if (EMFCompatHnSMod.isActionLegs() && planted && !procedural) {
            parts = new HashMap<>();
            parts.put("left_leg", new PoseSnapshot(model.leftLeg, true));
            parts.put("right_leg", new PoseSnapshot(model.rightLeg, true));
        }

        // The head stays on the camera while a swing twists the torso (see HnSHeadLook).
        // Rotation-only: where the head sits is the pack's to decide.
        if (EMFCompatHnSMod.isHeadLook() && HnSCompat.isAimedActionActive(player)) {
            float[] look = HnSHeadLook.headRotation(player);
            if (look != null) {
                if (parts == null) parts = new HashMap<>();
                parts.put("head", emfcompat$aimed(model.head, look));
            }
        }

        PoseManager.savePoses(
                uuid, SOURCE,
                arms[0],
                arms[1],
                parts,
                bodyBase
        );
    }

    /**
     * How long after an action the arms go on being eased into the stance, and the time the easing
     * works with. The mod brings the arms from the end of a swing back to the stance over the last
     * five ticks of the attack - 34 degrees a tick with a mace - which reads as a snap. From the
     * moment the animation turns back, the arms follow it with a lag instead, and the lag runs out
     * over the window after the action, so they arrive exactly, a little later.
     */
    @Unique
    private static final long SETTLE_NANOS = 600_000_000L;

    @Unique
    private static final double SETTLE_SECONDS = 0.2;

    /** Per player: the arms as last shown {left xRot, yRot, zRot, x, y, z, right ...}, when, and when the action was last seen. */
    @Unique
    private static final Map<UUID, double[]> SETTLING = new HashMap<>();

    /** The arms to hold this frame, {left, right}: as the model has them, or eased on the way back into a stance. */
    @Unique
    private static PoseSnapshot[] emfcompat$arms(PlayerModel<AbstractClientPlayer> model, UUID uuid,
                                                 boolean action, boolean returning, boolean stance) {
        long now = System.nanoTime();
        double[] shown = SETTLING.get(uuid);
        boolean settling = stance && shown != null && (returning || !action && now - (long) shown[13] < SETTLE_NANOS);
        if (!settling) {
            if (action) {
                // The action has the arms: remember where it has them, for when it turns back.
                if (shown == null) {
                    if (SETTLING.size() > 64) SETTLING.clear();
                    shown = new double[14];
                    SETTLING.put(uuid, shown);
                }
                emfcompat$read(model, shown);
                shown[12] = now;
                shown[13] = now;
            } else if (shown != null) {
                SETTLING.remove(uuid);
            }
            return new PoseSnapshot[]{new PoseSnapshot(model.leftArm), new PoseSnapshot(model.rightArm)};
        }
        // The lag is whole while the animation is still coming back and shrinks to nothing after it.
        double left = action ? 1 : 1 - (now - (long) shown[13]) / (double) SETTLE_NANOS;
        if (action) shown[13] = now;
        double dt = Math.min(0.1, (now - (long) shown[12]) * 1e-9);
        double k = 1 - Math.exp(-dt / Math.max(1e-3, SETTLE_SECONDS * left));
        double[] live = new double[12];
        emfcompat$read(model, live);
        for (int i = 0; i < 12; i++) {
            double d = live[i] - shown[i];
            if (i % 6 < 3) d = Math.atan2(Math.sin(d), Math.cos(d));
            shown[i] += d * k;
        }
        shown[12] = now;
        return new PoseSnapshot[]{emfcompat$posed(model.leftArm, shown, 0), emfcompat$posed(model.rightArm, shown, 6)};
    }

    @Unique
    private static void emfcompat$read(PlayerModel<AbstractClientPlayer> model, double[] out) {
        ModelPart[] arms = {model.leftArm, model.rightArm};
        for (int a = 0; a < 2; a++) {
            out[a * 6] = arms[a].xRot;
            out[a * 6 + 1] = arms[a].yRot;
            out[a * 6 + 2] = arms[a].zRot;
            out[a * 6 + 3] = arms[a].x;
            out[a * 6 + 4] = arms[a].y;
            out[a * 6 + 5] = arms[a].z;
        }
    }

    /** A snapshot of the arm as {@code pose} has it from {@code at}; the part itself is left as it was. */
    @Unique
    private static PoseSnapshot emfcompat$posed(ModelPart arm, double[] pose, int at) {
        float xRot = arm.xRot, yRot = arm.yRot, zRot = arm.zRot, x = arm.x, y = arm.y, z = arm.z;
        arm.setRotation((float) pose[at], (float) pose[at + 1], (float) pose[at + 2]);
        arm.setPos((float) pose[at + 3], (float) pose[at + 4], (float) pose[at + 5]);
        PoseSnapshot snapshot = new PoseSnapshot(arm);
        arm.setRotation(xRot, yRot, zRot);
        arm.setPos(x, y, z);
        return snapshot;
    }

    /** Whether the legs are held this frame: standing still, with hysteresis and a short hold. */
    @Unique
    private static boolean emfcompat$legsHeld(UUID uuid, AbstractClientPlayer player) {
        // Last tick's horizontal movement; the same for remote players, whose position is
        // interpolated tick by tick.
        double speed = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
        long[] gate = LEG_GATES.get(uuid);
        if (gate == null) {
            if (LEG_GATES.size() > 64) LEG_GATES.clear();
            gate = new long[]{speed <= LEGS_FREE_ABOVE ? 1 : 0, 0};
            LEG_GATES.put(uuid, gate);
        }
        boolean held = gate[0] == 1;
        boolean wantsSwitch = held ? speed > LEGS_FREE_ABOVE : speed < LEGS_HOLD_BELOW;
        if (!wantsSwitch) {
            gate[1] = 0;
            return held;
        }
        // Time, not calls: setupAnim runs more than once a frame (inventory, other views).
        long now = System.nanoTime();
        if (gate[1] == 0) {
            gate[1] = now;
        } else if (now - gate[1] >= LEGS_SWITCH_NANOS) {
            gate[0] = held ? 0 : 1;
            gate[1] = 0;
            return !held;
        }
        return held;
    }

    /** A rotation-only snapshot of the head turned to {x, y, z}; the part itself is left as it was. */
    @Unique
    private static PoseSnapshot emfcompat$aimed(ModelPart head, float[] look) {
        float xRot = head.xRot, yRot = head.yRot, zRot = head.zRot;
        head.xRot = look[0];
        head.yRot = look[1];
        head.zRot = look[2];
        PoseSnapshot aimed = new PoseSnapshot(head, true);
        head.xRot = xRot;
        head.yRot = yRot;
        head.zRot = zRot;
        return aimed;
    }
}
