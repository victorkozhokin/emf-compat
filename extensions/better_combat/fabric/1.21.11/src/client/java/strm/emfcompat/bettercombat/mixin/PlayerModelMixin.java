package strm.emfcompat.bettercombat.mixin;

import net.bettercombat.api.AttackHand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.bettercombat.EMFCompatBetterCombatClient;
import strm.emfcompat.bettercombat.compat.AttackPauseOverride;
import strm.emfcompat.bettercombat.compat.BetterCombatCompat;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Captures the arms during an active Better Combat attack.
 * The body, head and jacket remain under EMF's control so that resource-pack animations there keep running.
 *
 * <p>Player Animation Library applies its animation at {@code PlayerModel.setupAnim} RETURN with priority 2001,
 * so this mixin runs at priority 2500 to capture the pose <em>after</em> Better Combat has modified the model.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
public class PlayerModelMixin {

    @Unique
    private static final String SOURCE = "better_combat";

    @Unique
    private static final String POSE_SOURCE = EMFCompatBetterCombatClient.POSE_SOURCE;

    /** Below this walk-animation speed the player counts as stationary (legs get the attack step). */
    @Unique
    private static final float LEG_MOVE_THRESHOLD = 0.15f;

    @Inject(
            method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("RETURN")
    )
    private void emfcompat$captureBetterCombatArmPose(AvatarRenderState state, CallbackInfo ci) {
        PlayerModel model = (PlayerModel) (Object) this;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity entity = mc.level.getEntity(state.id);
        if (!(entity instanceof AbstractClientPlayer player)) return;

        UUID uuid = player.getUUID();

        if (!EMFCompatBetterCombatClient.isEnabled()) {
            PoseManager.clearPoses(uuid, SOURCE);
            PoseManager.clearPoses(uuid, POSE_SOURCE);
            return;
        }

        // Weapon stance (spear, trident, claymore…): a Better Combat pose controller holds the
        // player in it for as long as the weapon is held, independently of any attack. EMF pauses
        // while it runs, so without capturing it the whole model froze into the raw stance with no
        // pack animation left. Captured under its own lower-priority source so an attack still
        // wins the arms; arms only, since the player walks around in this pose and the legs must
        // keep EMF's walk cycle.
        emfcompat$captureWeaponPose(model, player, uuid);

        AttackHand attackHand = BetterCombatCompat.getAttackHand(player);
        if (attackHand == null) {
            // The attack is over as far as the pose goes — hand the arms back to EMF. But Better
            // Combat's PAL animation keeps fading out for a few frames after the attack hand is
            // cleared, and EMF pauses for as long as PAL is active. Without holding the pause
            // override across that fade the model drops to vanilla for those frames. The linger
            // cooldown then covers the hand-off itself.
            PoseManager.clearPoses(uuid, SOURCE);
            if (BetterCombatCompat.isAttackStackAnimating(player)) {
                AttackPauseOverride.markAttackActive(uuid);
            } else {
                AttackPauseOverride.tickCooldown(uuid);
            }
            return;
        }

        AttackPauseOverride.markAttackActive(uuid);

        // Body-follow: attack arm poses keep their shape and follow the torso (bodyBase = the
        // body's position at capture). Rotation-only (legacy): no bodyBase, arms keep only rotation.
        Vector3f bodyBase = EMFCompatBetterCombatClient.isBodyFollow()
                ? new Vector3f(model.body.x, model.body.y, model.body.z)
                : null;

        // Optionally restore the legs so an attack's step survives EMF. Two safeguards:
        //  - rotation-only: keeps the legs pivoted at the hip (absolute position detached them);
        //  - only while roughly stationary: when walking/running, leave the legs to EMF so they
        //    keep the walk cycle instead of freezing on the attack step.
        //    On 1.21.11 the limb swing lives on the render state as walkAnimationSpeed.
        Map<String, PoseSnapshot> parts = null;
        if (EMFCompatBetterCombatClient.isAttackLegs() && state.walkAnimationSpeed < LEG_MOVE_THRESHOLD) {
            parts = new HashMap<>();
            parts.put("left_leg", new PoseSnapshot(model.leftLeg, true));
            parts.put("right_leg", new PoseSnapshot(model.rightLeg, true));
        }

        PoseManager.savePoses(
                uuid, SOURCE,
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm),
                parts,
                bodyBase
        );
    }

    @Unique
    private static void emfcompat$captureWeaponPose(PlayerModel model, AbstractClientPlayer player, UUID uuid) {
        if (!BetterCombatCompat.isPoseStackActive(player)) {
            PoseManager.clearPoses(uuid, POSE_SOURCE);
            return;
        }

        Vector3f bodyBase = EMFCompatBetterCombatClient.isBodyFollow()
                ? new Vector3f(model.body.x, model.body.y, model.body.z)
                : null;

        PoseManager.savePoses(
                uuid, POSE_SOURCE,
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm),
                null,
                bodyBase
        );
    }
}
