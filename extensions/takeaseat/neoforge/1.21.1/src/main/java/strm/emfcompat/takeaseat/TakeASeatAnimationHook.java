package strm.emfcompat.takeaseat;

import com.takeaseat.client.TakeASeatClient;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.core.PoseSnapshot;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds the sitting pose against the resource pack while Take a Seat has the player seated.
 *
 * <p>The model is snapshotted before EMF animates and put back afterwards, so the pack's
 * animation is effectively skipped for the body — except for the head, which is deliberately
 * left to EMF so the player can still look around while seated.</p>
 *
 * <p>This used to be a pair of injections at the head and tail of
 * {@code EMFModelPartRoot#animate}; EMF 3.3 exposes the same two moments as hooks.</p>
 */
public final class TakeASeatAnimationHook extends EMFAnimationApi.EMFAnimationHook {

    private static final Logger LOGGER = LoggerFactory.getLogger("emf_compat");

    private static final ThreadLocal<Map<EMFModelPartVanilla, PoseSnapshot>> SNAPSHOTS =
            ThreadLocal.withInitial(HashMap::new);

    private TakeASeatAnimationHook() {
    }

    public static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new TakeASeatAnimationHook());
        } catch (Throwable t) {
            LOGGER.warn("[EMF Compat: Take a Seat] could not register the EMF animation hook", t);
        }
    }

    @Override
    public boolean onAnimationStart(AnimationContext context, boolean isCancelledByHook) {
        try {
            // Never cancels the animation: the head is supposed to keep playing it.
            SNAPSHOTS.get().clear();

            if (!TakeASeatEMFCompat.isEnabled()) return true;
            if (!ModList.get().isLoaded("takeaseat")) return true;

            EMFEntityRenderState state = context.activeState();
            if (state == null || state.isFirstPersonHand()) return true;
            if (!(state.emfEntity() instanceof Entity entity)) return true;
            if (!(entity instanceof AbstractClientPlayer player)) return true;

            IAnimation layer;
            try {
                layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, TakeASeatClient.SIT_LAYER);
            } catch (Exception e) {
                return true;
            }
            if (layer == null || !layer.isActive()) return true;

            EMFModelPartRoot root = context.animatingModelRoot();
            if (!root.modelName.toString().startsWith("player")) return true;

            Map<EMFModelPartVanilla, PoseSnapshot> snaps = SNAPSHOTS.get();
            for (Map.Entry<String, EMFModelPartVanilla> entry : root.getAllVanillaPartsByNameEMF().entrySet()) {
                String name = entry.getKey();
                if ("head".equals(name) || "hat".equals(name)) continue;
                snaps.put(entry.getValue(), new PoseSnapshot(entry.getValue()));
            }
            return true;
        } catch (Throwable t) {
            // A throw out of an EMF hook makes EMF drop the model's animations until resources reload.
            emfcompat$failed(t);
            return true;
        }
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        try {
            Map<EMFModelPartVanilla, PoseSnapshot> snaps = SNAPSHOTS.get();
            if (snaps.isEmpty()) return;

            snaps.forEach((part, snap) -> snap.apply(part));
            snaps.clear();
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
                TakeASeatAnimationHook.class.getSimpleName(), t);
    }
}
