package strm.emfcompat.tacz.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Vector3f;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.tacz.TACZEMFCompat;
import strm.emfcompat.tacz.compat.TACZCompat;

import java.util.HashMap;
import java.util.Map;

/**
 * Captures the gun pose after TACZ has posed the player model, so the core can restore it
 * after EMF re-animates the model.
 *
 * <p>TACZ poses the model inside {@code HumanoidModel.setupAnim} — either through
 * playerAnimator's layer stack (applied at {@code setupAnim} RETURN, priority 2000) or,
 * without playerAnimator, directly via its own {@code HumanoidModelMixin} at TAIL. This
 * mixin runs at priority 2500 so it captures the final pose of either path, for every
 * weapon.</p>
 *
 * <p>The arms are captured <em>full</em> (rotation + position + scale) together with the
 * body's neutral position, and restored by the core as <em>body-follow</em> poses: rotation
 * absolute, position shifted by the body's movement so the exact pose follows the torso
 * during movement and crouching. The head is captured <em>rotation-only</em> (it keeps EMF's
 * position so it stays on the neck; a full head snapshot detaches it). The body, legs and
 * face stay under EMF control (the face parts are children of the head and follow its
 * rotation while keeping their own animation).</p>
 *
 * <p>The head is captured only while playerAnimator drives the gun animation (it tilts the
 * head to aim); the vanilla-pose fallback does not move the head, so it is left to EMF.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
public class PlayerModelMixin {

    @Unique
    private static final String SOURCE = "tacz";

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void tacz$captureGunPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                     float ageInTicks, float netHeadYaw, float headPitch,
                                     CallbackInfo ci) {
        // TACZ's first-person pass calls setupAnim with ageInTicks == 0 and zeroes
        // the arm rotations — neither capture nor clear on that pass.
        if (ageInTicks == 0) {
            return;
        }
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        if (!TACZEMFCompat.isEnabled() || !TACZCompat.isGunPoseActive(player)) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
            return;
        }

        PlayerModel<AbstractClientPlayer> model = (PlayerModel<AbstractClientPlayer>) (Object) this;

        // Head rotation only, so it keeps EMF's body-following position.
        Map<String, PoseSnapshot> parts = null;
        if (TACZCompat.isPlayerAnimatorPosingHead(player)) {
            parts = new HashMap<>();
            parts.put("head", new PoseSnapshot(model.head, true));
        }

        // Body-follow: full arm pose + the neutral body position it was authored against; the
        // core restores rotation absolute and shifts the position by the body's movement so the
        // pose follows the torso. Rotation-only (legacy): no bodyBase, arms keep only rotation.
        Vector3f bodyBase = TACZEMFCompat.isBodyFollow()
                ? new Vector3f(model.body.x, model.body.y, model.body.z)
                : null;
        PoseManager.savePoses(
                player.getUUID(), SOURCE,
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm),
                parts,
                bodyBase
        );
    }
}
