package strm.emfcompat.quark.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.core.SavedPoses;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;

import java.util.Map;
import java.util.UUID;

/**
 * Applies saved Quark poses on top of EMF's animation.
 * <p>
 * Core's mixin skips pose restoration for the local player in first-person view. Shoulder-surfing
 * camera mods can keep the camera type as first-person even when the body is visible, so Quark
 * emotes would be invisible. This mixin applies the Quark source directly in those cases.
 * In non-first-person views core already handles the restore, so we do nothing there.
 * </p>
 */
@Mixin(value = EMFModelPartRoot.class, priority = 1100)
public class EMFModelPartRootMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("QuarkCompat");
    private static final String SOURCE = "quark";

    @Unique
    private long emfcompat$lastQuarkFrame = -1;

    private static int emfcompat$logCounter = 0;

    @Inject(method = "animate", at = @At("RETURN"))
    private void emfcompat$applyQuarkPoses(CallbackInfo ci) {
        boolean shouldLog = ++emfcompat$logCounter % 60 == 0;

        var state = EMFAnimationEntityContext.getEmfState();
        if (state == null || state.emfEntity() == null) {
            return;
        }

        UUID uuid = UUID.fromString(state.emfEntity().etf$getUuid().toString());
        SavedPoses quarkPoses = PoseManager.getSavedPoses(uuid, SOURCE);
        if (quarkPoses == null) {
            return;
        }

        if (shouldLog) {
            LOGGER.info("[QuarkCompat] EMFModelPartRoot.animate RETURN for {}: hasQuarkPoses, firstPerson={}",
                    uuid, EMFCompatCore.isLocalPlayerInFirstPerson(uuid));
        }

        // Core already restores poses for non-first-person views; skip to avoid double application.
        if (!EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) {
            return;
        }

        if (emfcompat$lastQuarkFrame == PoseManager.currentFrame) {
            return;
        }
        emfcompat$lastQuarkFrame = PoseManager.currentFrame;

        EMFModelPartRoot root = (EMFModelPartRoot) (Object) this;
        Map<String, PoseSnapshot> parts = quarkPoses.parts();
        if (parts == null && quarkPoses.leftArm() == null && quarkPoses.rightArm() == null) {
            return;
        }

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

        boolean hasLeftArmInParts = parts != null && parts.containsKey("left_arm");
        boolean hasRightArmInParts = parts != null && parts.containsKey("right_arm");

        for (EMFModelPartVanilla part : root.getAllVanillaPartsEMF()) {
            String name = emfcompat$vanillaPartName(part.toStringShort());
            if (name == null) continue;

            if (parts != null) {
                PoseSnapshot snap = parts.get(name);
                if (snap != null) snap.apply(part);
            }

            switch (name) {
                case "head" -> headPart = part;
                case "headwear" -> headwearPart = part;
                case "left_arm" -> {
                    if (!hasLeftArmInParts && quarkPoses.leftArm() != null) {
                        quarkPoses.leftArm().applyRotation(part);
                    }
                    leftArmPart = part;
                }
                case "right_arm" -> {
                    if (!hasRightArmInParts && quarkPoses.rightArm() != null) {
                        quarkPoses.rightArm().applyRotation(part);
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

        if (shouldLog) LOGGER.info("[QuarkCompat] Applied Quark poses for {} in first-person/shoulder view", uuid);
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
