package strm.emfcompat.carryon;

import strm.emfcompat.core.BodyPartSync;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;

import java.util.UUID;

/**
 * Legacy object-sync support: records the EMF-animated torso pose once EMF has finished animating,
 * so {@code CarryRenderHelperMixin} can move the carried block or entity with the torso through
 * {@link BodyPartSync} (translation + rotation).
 *
 * <p>Only used when the Carry On "Arm sync" option is set to legacy (rotation-only). In the default
 * body-follow mode the carried object rides the body-follow delta the core publishes instead, and
 * this capture is skipped entirely.</p>
 *
 * <p>Replaces the mixin this addon used to put on {@code EMFModelPartRoot}: EMF 3.3 dropped the
 * second animation entry point that mixin also had to hook, and its hook covers both paths.</p>
 */
public final class CarryOnAnimationHook extends EMFAnimationApi.EMFAnimationHook {

    private CarryOnAnimationHook() {
    }

    public static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new CarryOnAnimationHook());
        } catch (Throwable t) {
            System.err.println("[EMF Compat: Carry On] could not register the EMF animation hook: " + t);
        }
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        if (!EMFCarryOnClient.isEnabled() || EMFCarryOnClient.isBodyFollow()) {
            return;
        }

        EMFEntityRenderState state = context.activeState();
        if (state == null) return;

        UUID uuid = state.uuid();
        if (uuid == null) return;

        EMFModelPartVanilla body = context.animatingModelRoot().getAllVanillaPartsByNameEMF().get("body");
        if (body != null) {
            BodyPartSync.captureCurrent(uuid, "body", body);
        }
    }
}
