package strm.emfcompat.watut.mixin;

import net.minecraft.client.model.PlayerModel;
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
import strm.emfcompat.watut.compat.WatutCompat;

import java.util.Map;

/**
 * Captures the status poses that WATUT applies at the TAIL of {@code PlayerModel.setupAnim}
 * (its own mixin runs at default priority, so by the time this RETURN injection runs at
 * priority 2500 the pose is final). The core mixins restore the captured parts after EMF
 * applies its resource-pack animation, so GUI / typing / idle poses survive EMF.
 *
 * <p>Arms are captured via the legacy arm fields (core applies them rotation-only): WATUT
 * also nudges arm positions, but freezing those would detach the arms from EMF's body-follow
 * animation while walking. The head is captured rotation-only for the same reason; the hat
 * is saved explicitly under both EMF namings because not all cores sync it from the head.
 * Body and legs always stay under EMF's control.</p>
 */
@Mixin(value = PlayerModel.class, priority = 2500)
public class PlayerModelMixin {

    @Unique
    private static final String SOURCE = "watut";

    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void emfcompat$captureWatutPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        if (EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())) return;

        WatutCompat.WatutPose pose = WatutCompat.getPosedParts(player);
        if (pose == null) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
            return;
        }

        PlayerModel<?> model = (PlayerModel<?>) (Object) this;

        PoseSnapshot leftArm = pose.arms() ? new PoseSnapshot(model.leftArm) : null;
        PoseSnapshot rightArm = pose.arms() ? new PoseSnapshot(model.rightArm) : null;

        Map<String, PoseSnapshot> parts = Map.of();
        if (pose.head()) {
            PoseSnapshot hatPose = new PoseSnapshot(model.hat, true);
            parts = Map.of(
                    "head", new PoseSnapshot(model.head, true),
                    "hat", hatPose,
                    "headwear", hatPose);
        }

        PoseManager.savePoses(player.getUUID(), SOURCE, leftArm, rightArm, parts);
    }
}
