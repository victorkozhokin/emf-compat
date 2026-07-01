package strm.emfcompat.corpse;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traben.entity_model_features.models.IEMFModel;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tracks when the Corpse renderer is actively drawing a corpse and temporarily
 * forces the dummy player/skeleton renderer onto its vanilla EMF variant so the
 * body does not inherit the shared custom model pose from the real player.
 *
 * <p>Corpse renders the dead body by creating an internal {@code DummyPlayer} or
 * {@code DummySkeleton} and passing it through the regular player/skeleton renderer.
 * EMF replaces those renderers' models with a shared custom model. Because the model
 * is shared, simply pausing animation for the corpse leaves the parts in whatever pose
 * the real player last wrote into them. Instead, we push the current EMF model variant
 * onto a stack, switch to variant {@code 0} (the captured vanilla model) for the duration
 * of the dummy render, and restore the original variant afterwards.</p>
 */
public final class CorpseTracker {

    private CorpseTracker() {
    }

    public static final Logger LOGGER = LoggerFactory.getLogger("EMFCompat/Corpse");

    private static final ThreadLocal<Boolean> RENDERING_CORPSE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Deque<Integer>> VARIANT_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private static boolean initialized = false;

    /**
     * Initializes the compatibility layer. Called once from the mod entry point during
     * client setup, after EMF and Corpse are loaded.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        LOGGER.info("[EMFCompat:Corpse] Corpse EMF compatibility initialized");
    }

    /**
     * Marks the current thread as being inside the Corpse renderer.
     * Called by the CorpseRenderer mixin at the start/end of the render entry point.
     */
    public static void setRenderingCorpse(boolean rendering) {
        RENDERING_CORPSE.set(rendering);
        LOGGER.debug("[EMFCompat:Corpse] render pass flag set to: {}", rendering);
    }

    public static boolean isRenderingCorpse() {
        return RENDERING_CORPSE.get();
    }

    /**
     * Forces the renderer's EMF model to its vanilla variant (variant {@code 0}) if it
     * is currently customized by EMF. The previous variant is saved so it can be restored
     * by {@link #popVanillaModelState(EntityRenderer)}.
     */
    public static void pushVanillaModelState(EntityRenderer<?> renderer) {
        if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) {
            return;
        }
        EntityModel<?> model = living.getModel();
        if (!(model instanceof IEMFModel emfModel) || !emfModel.emf$isEMFModel()) {
            return;
        }

        EMFModelPartRoot root = emfModel.emf$getEMFRootModel();
        int currentVariant = root.currentModelVariant;
        VARIANT_STACK.get().push(currentVariant);
        root.setVariantStateTo(0);
        LOGGER.debug("[EMFCompat:Corpse] Forced vanilla model variant for {} (was variant {})",
                renderer.getClass().getSimpleName(), currentVariant);
    }

    /**
     * Restores the EMF model variant saved by {@link #pushVanillaModelState(EntityRenderer)}.
     */
    public static void popVanillaModelState(EntityRenderer<?> renderer) {
        if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) {
            return;
        }
        EntityModel<?> model = living.getModel();
        if (!(model instanceof IEMFModel emfModel) || !emfModel.emf$isEMFModel()) {
            return;
        }

        Deque<Integer> stack = VARIANT_STACK.get();
        if (stack.isEmpty()) {
            return;
        }
        int previousVariant = stack.pop();
        emfModel.emf$getEMFRootModel().setVariantStateTo(previousVariant);
        LOGGER.debug("[EMFCompat:Corpse] Restored model variant {} for {}",
                previousVariant, renderer.getClass().getSimpleName());
    }
}
