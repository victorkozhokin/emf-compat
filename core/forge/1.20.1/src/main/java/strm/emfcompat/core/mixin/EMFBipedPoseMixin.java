package strm.emfcompat.core.mixin;

import net.minecraft.client.model.HumanoidModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import traben.entity_model_features.models.animation.state.EMFBipedPose;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.SavedPoses;

import java.util.UUID;

@Mixin(EMFBipedPose.class)
public class EMFBipedPoseMixin {

    @Inject(method = "applyTo", at = @At("RETURN"), remap = false)
    private void emfcompat$restoreArmorPose(HumanoidModel<?> model, CallbackInfo ci) {
        var state = EMFAnimationEntityContext.getEmfState();
        if (state == null || state.emfEntity() == null) return;

        UUID uuid = state.emfEntity().etf$getUuid();
        if (EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;

        SavedPoses savedPoses = PoseManager.getSavedPoses(uuid);
        if (savedPoses == null) return;

        if (savedPoses.leftArm() != null) savedPoses.leftArm().applyRotation(model.leftArm);
        if (savedPoses.rightArm() != null) savedPoses.rightArm().applyRotation(model.rightArm);
    }
}
