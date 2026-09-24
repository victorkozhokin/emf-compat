package strm.emfcompat.animationadditions.footgrounding.compat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;

import java.util.Map;
import java.util.UUID;

/**
 * Adds the leg offset on top of whatever the model was animated to. EMF calls
 * {@code onAnimationEnd} once per entity render, after the pack animation and after the core has
 * put captured poses back (the core's hook is registered first), so walking, crouching and other
 * addons' poses all stay and only get the step added to them.
 *
 * <p>Armour follows by itself: EMF copies the animated biped pose onto the armour model after this.</p>
 */
public final class FootGroundingHook extends EMFAnimationApi.EMFAnimationHook {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatFootGrounding");

    private FootGroundingHook() {
    }

    public static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new FootGroundingHook());
        } catch (Throwable t) {
            LOGGER.warn("[FootGrounding] could not register the EMF animation hook", t);
        }
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        try {
            EMFEntityRenderState state = context.activeState();
            if (state == null || state.isFirstPersonHand()) return;
            UUID uuid = state.uuid();
            if (uuid == null) return;
            float[] right = FootGrounding.legOffset(uuid, true);
            float[] left = FootGrounding.legOffset(uuid, false);
            if (right == null && left == null) return;
            Map<String, EMFModelPartVanilla> parts = context.animatingModelRoot().getAllVanillaPartsByNameEMF();
            apply(parts.get("right_leg"), right);
            apply(parts.get("left_leg"), left);
        } catch (Throwable t) {
            // A throw out of an animation hook makes EMF disable the model's animations for good.
        }
    }

    private static void apply(EMFModelPartVanilla leg, float[] offset) {
        if (leg == null || offset == null) return;
        leg.xRot += offset[0];
        leg.y -= offset[1];
    }
}
