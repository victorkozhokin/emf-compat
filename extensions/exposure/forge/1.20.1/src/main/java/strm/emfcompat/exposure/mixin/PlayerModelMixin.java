package strm.emfcompat.exposure.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.exposure.compat.ExposureCompat;

import java.util.Map;

/**
 * Captures the camera poses that Exposure applies inside {@code HumanoidModel.setupAnim}
 * (its client mixin injects before the arm-bobbing calls, so by the time this RETURN
 * injection runs at priority 2500 the pose is final). The core mixins restore the
 * captured parts after EMF applies its resource-pack animation, so a player using a
 * camera keeps holding it instead of playing pack animations.
 *
 * <p>One named source per camera state. Aiming / stand / disassembled poses also move the
 * head, so the head and hat are captured too (the hat explicitly - the 1.20.1 core does not
 * sync the head onto the headwear layer);
 * the selfie pose only moves the camera arm. Body and legs always stay under EMF's control.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
public class PlayerModelMixin {

    @Unique
    private static final String AIMING_SOURCE = "exposure_aiming";
    @Unique
    private static final String SELFIE_SOURCE = "exposure_selfie";
    @Unique
    private static final String STAND_SOURCE = "exposure_stand";
    @Unique
    private static final String DISASSEMBLED_SOURCE = "exposure_disassembled";

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$captureCameraPoses(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                              float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        if (EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())) return;

        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        ExposureCompat.CameraPose pose = ExposureCompat.getCameraPose(player);

        emfcompat$captureBothArmsAndHead(player, model, pose == ExposureCompat.CameraPose.AIMING, AIMING_SOURCE);
        emfcompat$captureBothArmsAndHead(player, model, pose == ExposureCompat.CameraPose.STAND, STAND_SOURCE);
        emfcompat$captureBothArmsAndHead(player, model, pose == ExposureCompat.CameraPose.DISASSEMBLED, DISASSEMBLED_SOURCE);

        if (pose == ExposureCompat.CameraPose.SELFIE) {
            HumanoidArm arm = ExposureCompat.getCameraArm(player);
            PoseSnapshot cameraArm = arm == HumanoidArm.LEFT
                    ? new PoseSnapshot(model.leftArm)
                    : new PoseSnapshot(model.rightArm);
            if (arm == HumanoidArm.LEFT) {
                PoseManager.savePoses(player.getUUID(), SELFIE_SOURCE, cameraArm, null);
            } else {
                PoseManager.savePoses(player.getUUID(), SELFIE_SOURCE, null, cameraArm);
            }
        } else {
            PoseManager.clearPoses(player.getUUID(), SELFIE_SOURCE);
        }
    }

    @Unique
    private void emfcompat$captureBothArmsAndHead(Player player, PlayerModel<?> model,
                                                  boolean active, String source) {
        if (active) {
            // Exposure only rotates the head, so capture it rotation-only: a full snapshot
            // would freeze the head position and detach it from EMF's body-follow animation
            // (same issue the Quark addon had).
            // The hat layer must be saved explicitly (under both EMF namings): the 1.20.1
            // core has no head->headwear sync, so without this the hair detaches from the head.
            PoseSnapshot hatPose = new PoseSnapshot(model.hat, true);
            PoseManager.savePoses(player.getUUID(), source,
                    new PoseSnapshot(model.leftArm), new PoseSnapshot(model.rightArm),
                    Map.of("head", new PoseSnapshot(model.head, true),
                            "hat", hatPose,
                            "headwear", hatPose));
        } else {
            PoseManager.clearPoses(player.getUUID(), source);
        }
    }
}
