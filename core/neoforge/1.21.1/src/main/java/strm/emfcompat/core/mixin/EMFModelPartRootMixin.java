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

import java.util.Map;
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

        SavedPoses savedPoses = PoseManager.getSavedPoses(uuid);
        if (savedPoses == null) return;

        EMFModelPartRoot root = (EMFModelPartRoot) (Object) this;
        EMFModelPartVanilla headPart = null;
        EMFModelPartVanilla headwearPart = null;
        EMFModelPartVanilla leftArmPart = null;
        EMFModelPartVanilla rightArmPart = null;
        EMFModelPartVanilla leftSleeve = null;
        EMFModelPartVanilla rightSleeve = null;
        EMFModelPartVanilla leftLegPart = null;
        EMFModelPartVanilla rightLegPart = null;
        EMFModelPartVanilla leftPants = null;
        EMFModelPartVanilla rightPants = null;
        EMFModelPartVanilla bodyPart = null;
        EMFModelPartVanilla jacket = null;

        Map<String, PoseSnapshot> partMap = savedPoses.parts();
        boolean hasLeftArmInParts = partMap != null && partMap.containsKey("left_arm");
        boolean hasRightArmInParts = partMap != null && partMap.containsKey("right_arm");

        for (EMFModelPartVanilla part : root.getAllVanillaPartsEMF()) {
            String name = emfcompat$vanillaPartName(part.toStringShort());
            if (name == null) continue;

            if (partMap != null) {
                PoseSnapshot snap = partMap.get(name);
                if (snap != null) snap.apply(part);
            }

            switch (name) {
                case "head" -> headPart = part;
                case "headwear" -> headwearPart = part;
                case "left_arm" -> {
                    if (!hasLeftArmInParts && savedPoses.leftArm() != null) {
                        savedPoses.leftArm().applyRotation(part);
                    }
                    leftArmPart = part;
                }
                case "right_arm" -> {
                    if (!hasRightArmInParts && savedPoses.rightArm() != null) {
                        savedPoses.rightArm().applyRotation(part);
                    }
                    rightArmPart = part;
                }
                case "left_sleeve" -> leftSleeve = part;
                case "right_sleeve" -> rightSleeve = part;
                case "left_leg" -> leftLegPart = part;
                case "right_leg" -> rightLegPart = part;
                case "left_pants" -> leftPants = part;
                case "right_pants" -> rightPants = part;
                case "body" -> bodyPart = part;
                case "jacket" -> jacket = part;
            }
        }

        if (headPart != null && headwearPart != null && !headPart.hasChild("headwear")) {
            new PoseSnapshot(headPart).apply(headwearPart);
        }
        if (leftArmPart != null && leftSleeve != null && !leftArmPart.hasChild("left_sleeve")) {
            new PoseSnapshot(leftArmPart).apply(leftSleeve);
        }
        if (rightArmPart != null && rightSleeve != null && !rightArmPart.hasChild("right_sleeve")) {
            new PoseSnapshot(rightArmPart).apply(rightSleeve);
        }
        if (leftLegPart != null && leftPants != null && !leftLegPart.hasChild("left_pants")) {
            new PoseSnapshot(leftLegPart).apply(leftPants);
        }
        if (rightLegPart != null && rightPants != null && !rightLegPart.hasChild("right_pants")) {
            new PoseSnapshot(rightLegPart).apply(rightPants);
        }
        if (bodyPart != null && jacket != null && !bodyPart.hasChild("jacket")) {
            new PoseSnapshot(bodyPart).apply(jacket);
        }
    }

    @Unique
    private static String emfcompat$vanillaPartName(String shortName) {
        String prefix = "[vanilla part ";
        if (!shortName.startsWith(prefix) || !shortName.endsWith("]")) {
            return null;
        }
        return shortName.substring(prefix.length(), shortName.length() - 1);
    }
}
