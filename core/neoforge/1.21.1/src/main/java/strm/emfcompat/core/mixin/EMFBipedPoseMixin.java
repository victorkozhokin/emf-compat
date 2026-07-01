package strm.emfcompat.core.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import traben.entity_model_features.models.animation.state.EMFBipedPose;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.core.SavedPoses;

import java.util.Map;
import java.util.UUID;

@Mixin(EMFBipedPose.class)
public class EMFBipedPoseMixin {

    @Inject(method = "applyTo", at = @At("RETURN"))
    private void emfcompat$restoreArmorPose(HumanoidModel<?> model, CallbackInfo ci) {
        var state = EMFAnimationEntityContext.getEmfState();
        if (state == null || state.emfEntity() == null) return;

        UUID uuid = state.emfEntity().etf$getUuid();
        if (EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;

        SavedPoses savedPoses = PoseManager.getSavedPoses(uuid);
        if (savedPoses == null) return;

        Map<String, PoseSnapshot> parts = savedPoses.parts();
        if (parts != null) {
            emfcompat$applyIfPresent(parts, "head", model.head);
            emfcompat$applyIfPresent(parts, "body", model.body);
            emfcompat$applyIfPresent(parts, "left_arm", model.leftArm);
            emfcompat$applyIfPresent(parts, "right_arm", model.rightArm);
            emfcompat$applyIfPresent(parts, "left_leg", model.leftLeg);
            emfcompat$applyIfPresent(parts, "right_leg", model.rightLeg);
        }

        if (savedPoses.leftArm() != null && (parts == null || !parts.containsKey("left_arm"))) {
            savedPoses.leftArm().applyRotation(model.leftArm);
        }
        if (savedPoses.rightArm() != null && (parts == null || !parts.containsKey("right_arm"))) {
            savedPoses.rightArm().applyRotation(model.rightArm);
        }
    }

    private static void emfcompat$applyIfPresent(Map<String, PoseSnapshot> parts, String name, ModelPart part) {
        PoseSnapshot snap = parts.get(name);
        if (snap != null) snap.apply(part);
    }
}
