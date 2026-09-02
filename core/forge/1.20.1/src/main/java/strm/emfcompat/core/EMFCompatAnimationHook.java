package strm.emfcompat.core;

import net.minecraft.client.model.HumanoidModel;
import org.joml.Vector3f;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFBipedPose;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.animation.state.EMFState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;

import java.util.Map;
import java.util.UUID;

/**
 * Restores captured poses onto the EMF model, through EMF's own animation hooks.
 *
 * <p>This replaces the mixins the core used to put on {@code EMFModelPartRoot} and
 * {@code EMFBipedPose}. EMF calls {@code onAnimationEnd} once per entity render, right after the
 * resource-pack animation has been applied and before anything is drawn — the exact point the
 * mixins were reaching for, but as a contract rather than an injection into internals.</p>
 *
 * <p>Only {@code onAnimationEnd} is used. {@code onAnimationStart} could cancel EMF's animation
 * outright, which would defeat the point: the parts an addon does not claim are supposed to keep
 * playing the pack's animation.</p>
 */
public final class EMFCompatAnimationHook extends EMFAnimationApi.EMFAnimationHook {

    private EMFCompatAnimationHook() {
    }

    /** Called once from each loader's client init. */
    public static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new EMFCompatAnimationHook());
        } catch (Throwable t) {
            // Nothing restores poses without the hook, but the game itself is still fine.
            System.err.println("[EMF Compat] could not register the EMF animation hook: " + t);
        }
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        emfcompat$restore(context.activeState(), context.animatingModelRoot());
    }

    /**
     * Re-applies the captured pose to the armour model. EMF copies the animated biped pose onto
     * humanoid models that the render pipeline detaches (armour, and the repeated player-model
     * calls made by cosmetics mods), so without this the body moves and the armour does not.
     */
    @Override
    public void onBipedPoseCopyEnd(EMFBipedPose pose, HumanoidModel<?> model, boolean wasCancelledByHook) {
        // The biped hook carries no state of its own; this copy always belongs to the entity
        // being rendered right now.
        EMFEntityRenderState state = EMFState.state();
        if (state == null) return;

        UUID uuid = state.uuid();
        if (uuid == null) return;
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

    private static void emfcompat$applyIfPresent(Map<String, PoseSnapshot> parts, String name,
                                                 net.minecraft.client.model.geom.ModelPart part) {
        PoseSnapshot snap = parts.get(name);
        // Full pose (rotation + position + scale): a sitting pose moves parts, not just rotates
        // them, so rotation-only left the armor at standing pivots (detached from the body).
        if (snap != null) snap.apply(part);
    }

    private static void emfcompat$restore(EMFEntityRenderState state, EMFModelPartRoot root) {
        PoseManager.cleanupIfNeeded();

        if (state == null) return;
        UUID uuid = state.uuid();
        if (uuid == null) return;
        if (EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;

        SavedPoses savedPoses = PoseManager.getSavedPoses(uuid);
        if (savedPoses == null) return;

        // EMF hands out its vanilla parts already keyed by name, so the pose map can be applied
        // directly instead of walking every part and parsing its printed name.
        Map<String, EMFModelPartVanilla> byName = root.getAllVanillaPartsByNameEMF();
        Map<String, PoseSnapshot> partMap = savedPoses.parts();

        if (partMap != null) {
            for (Map.Entry<String, PoseSnapshot> entry : partMap.entrySet()) {
                EMFModelPartVanilla part = byName.get(entry.getKey());
                if (part != null) entry.getValue().apply(part);
            }
        }

        EMFModelPartVanilla headPart = byName.get("head");
        EMFModelPartVanilla headwearPart = byName.get("headwear");
        if (headwearPart == null) headwearPart = byName.get("hat");
        EMFModelPartVanilla leftArmPart = byName.get("left_arm");
        EMFModelPartVanilla rightArmPart = byName.get("right_arm");
        EMFModelPartVanilla leftSleeve = byName.get("left_sleeve");
        EMFModelPartVanilla rightSleeve = byName.get("right_sleeve");
        EMFModelPartVanilla leftLegPart = byName.get("left_leg");
        EMFModelPartVanilla rightLegPart = byName.get("right_leg");
        EMFModelPartVanilla leftPants = byName.get("left_pants");
        EMFModelPartVanilla rightPants = byName.get("right_pants");
        EMFModelPartVanilla bodyPart = byName.get("body");
        EMFModelPartVanilla jacket = byName.get("jacket");

        boolean hasLeftArmInParts = partMap != null && partMap.containsKey("left_arm");
        boolean hasRightArmInParts = partMap != null && partMap.containsKey("right_arm");

        // Arms: rotation absolute, position optionally offset by the body's movement since
        // capture (body-follow) when a bodyBase was supplied.
        Vector3f bodyDelta = null;
        Vector3f bodyBase = savedPoses.bodyBase();
        if (bodyBase != null && bodyPart != null) {
            bodyDelta = new Vector3f(bodyPart.x - bodyBase.x(), bodyPart.y - bodyBase.y(), bodyPart.z - bodyBase.z());
        }
        // Publish the delta so hand-attached objects (e.g. Carry On's carried block) can move
        // by the same amount and stay in sync with the arms.
        PoseManager.setBodyFollowDelta(uuid, bodyDelta);
        if (!hasLeftArmInParts && savedPoses.leftArm() != null && leftArmPart != null) {
            emfcompat$applyArm(leftArmPart, savedPoses.leftArm(), bodyDelta);
        }
        if (!hasRightArmInParts && savedPoses.rightArm() != null && rightArmPart != null) {
            emfcompat$applyArm(rightArmPart, savedPoses.rightArm(), bodyDelta);
        }

        if (headPart != null && headwearPart != null
                && !headPart.hasChild("headwear") && !headPart.hasChild("hat")) {
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

    private static void emfcompat$applyArm(EMFModelPartVanilla part, PoseSnapshot snap, Vector3f bodyDelta) {
        // Rotation is always absolute. Without a body delta this is rotation-only (the arm
        // keeps EMF's position); with one, the pose position follows the moved torso.
        snap.applyRotation(part);
        if (bodyDelta != null) {
            part.x = snap.x + bodyDelta.x;
            part.y = snap.y + bodyDelta.y;
            part.z = snap.z + bodyDelta.z;
            part.xScale = snap.xScale;
            part.yScale = snap.yScale;
            part.zScale = snap.zScale;
            part.visible = snap.visible;
        }
    }
}
