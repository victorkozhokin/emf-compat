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

        emfcompat$captureStance(model, uuid, bodyBase, player);

        if (!HnSCompat.isActionActive(player)) {
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
        if (EMFCompatHnSMod.isActionLegs() && emfcompat$legsHeld(uuid, player)) {
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
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm),
                parts,
                bodyBase
        );
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

    @Unique
    private static void emfcompat$captureStance(PlayerModel<AbstractClientPlayer> model, UUID uuid,
                                                Vector3f bodyBase, AbstractClientPlayer player) {
        if (!EMFCompatHnSMod.isStances() || !HnSCompat.isStanceActive(player)) {
            PoseManager.clearPoses(uuid, POSE_SOURCE);
            return;
        }

        // Arms only: the player walks around in this pose, so the legs have to keep EMF's cycle.
        PoseManager.savePoses(
                uuid, POSE_SOURCE,
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm),
                null,
                bodyBase
        );
    }
}
