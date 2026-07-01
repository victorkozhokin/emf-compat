package strm.emfcompat.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.core.SavedPoses;

import java.util.UUID;

@Mixin(EMFModelPartRoot.class)
public class EMFModelPartRootMixin {

    @Unique
    private long emfcompat$lastRestoreFrame = -1;

    @Inject(method = "animate", at = @At("RETURN"))
    private void emfcompat$restorePosesAfterAnimate(CallbackInfo ci) {
        if (emfcompat$lastRestoreFrame == PoseManager.currentFrame) return;
        emfcompat$lastRestoreFrame = PoseManager.currentFrame;

        PoseManager.cleanupIfNeeded();

        var state = EMFAnimationEntityContext.getEmfState();
        if (state == null || state.emfEntity() == null) return;

        UUID uuid = state.emfEntity().etf$getUuid();
        if (EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;

        SavedPoses savedPoses = PoseManager.entitySavedPoses.get(uuid);
        if (savedPoses == null) return;

        EMFModelPartRoot root = (EMFModelPartRoot) (Object) this;
        EMFModelPartVanilla leftArmPart = null;
        EMFModelPartVanilla rightArmPart = null;
        EMFModelPartVanilla leftSleeve = null;
        EMFModelPartVanilla rightSleeve = null;

        for (EMFModelPartVanilla part : root.getAllVanillaPartsEMF()) {
            String shortName = part.toStringShort();
            if ("[vanilla part left_arm]".equals(shortName)) {
                if (savedPoses.leftArm() != null) {
                    savedPoses.leftArm().applyRotation(part);
                    leftArmPart = part;
                }
            } else if ("[vanilla part right_arm]".equals(shortName)) {
                if (savedPoses.rightArm() != null) {
                    savedPoses.rightArm().applyRotation(part);
                    rightArmPart = part;
                }
            } else if ("[vanilla part left_sleeve]".equals(shortName)) {
                leftSleeve = part;
            } else if ("[vanilla part right_sleeve]".equals(shortName)) {
                rightSleeve = part;
            }
        }

        if (leftArmPart != null && leftSleeve != null && !leftArmPart.hasChild("left_sleeve")) {
            new PoseSnapshot(leftArmPart).apply(leftSleeve);
        }
        if (rightArmPart != null && rightSleeve != null && !rightArmPart.hasChild("right_sleeve")) {
            new PoseSnapshot(rightArmPart).apply(rightSleeve);
        }
    }
}
