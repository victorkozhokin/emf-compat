package strm.emfcompat.immersivemelodies.compat;

import net.minecraft.client.model.geom.ModelPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Uncrosses an illager's arms while it plays an instrument.
 *
 * <p>An illager's model draws its arms folded — one {@code arms} part — unless it is attacking or
 * casting, and only then shows {@code left_arm} and {@code right_arm}. Immersive Melodies poses the
 * separate arms but never switches them on, so a vindicator or an evoker with an instrument keeps
 * its arms crossed, with or without a resource pack, and the renderer does not even draw the
 * instrument: those two draw a held item only while attacking or casting. (The
 * pillager is spared: its idle pose already shows both arms.)</p>
 *
 * <p>Without a pack the switch happens in the model's own {@code setupAnim}
 * ({@code IllagerModelMixin}). Under EMF the pack decides visibility again after that — Fresh
 * Animations folds the vindicator's arms by its own variables — so the same switch is repeated
 * here, after the pack's animation.</p>
 */
public final class IllagerArms extends EMFAnimationApi.EMFAnimationHook {

    private static final Logger LOGGER = LoggerFactory.getLogger("emf_compat");

    /** Illagers currently playing, as last seen by the capture. Render thread only. */
    private static final Set<UUID> PLAYING = new HashSet<>();

    private IllagerArms() {
    }

    public static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new IllagerArms());
        } catch (Throwable t) {
            LOGGER.warn("[EMF Compat] could not register the Immersive Melodies illager hook", t);
        }
    }

    public static void setPlaying(UUID uuid, boolean playing) {
        if (playing) {
            PLAYING.add(uuid);
        } else {
            PLAYING.remove(uuid);
        }
    }

    /**
     * Whether this illager is playing right now — the vindicator's and evoker's item layers ask,
     * because they only draw the held item while the arms are out for a fight or a spell.
     */
    public static boolean isPlaying(UUID uuid) {
        return PLAYING.contains(uuid);
    }

    /** Shows the separate arms and hides the folded ones. */
    public static void uncross(ModelPart arms, ModelPart leftArm, ModelPart rightArm) {
        if (arms != null) arms.visible = false;
        if (leftArm != null) leftArm.visible = true;
        if (rightArm != null) rightArm.visible = true;
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        try {
            EMFEntityRenderState state = context.activeState();
            if (state == null || PLAYING.isEmpty()) return;
            UUID uuid = state.uuid();
            if (uuid == null || !PLAYING.contains(uuid)) return;

            Map<String, EMFModelPartVanilla> byName = context.animatingModelRoot().getAllVanillaPartsByNameEMF();
            uncross(byName.get("arms"), byName.get("left_arm"), byName.get("right_arm"));
        } catch (Throwable t) {
            // A throw out of an EMF hook makes EMF drop the model's animations until resources reload.
            emfcompat$failed(t);
        }
    }

    private static boolean emfcompat$failureLogged;

    private static void emfcompat$failed(Throwable t) {
        if (emfcompat$failureLogged) return;
        emfcompat$failureLogged = true;
        org.slf4j.LoggerFactory.getLogger("emf_compat").warn("[EMF Compat] {} failed; skipped so EMF keeps animating the model",
                IllagerArms.class.getSimpleName(), t);
    }
}
