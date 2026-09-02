package strm.emfcompat.takeaseat;

import com.seatify.client.SeatifyClient;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.core.SavedPoses;
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
 * left to EMF so the player can still look around while seated. The same snapshots are published
 * to the core so the armour gets the sitting pose too.</p>
 *
 * <p>This used to be a pair of injections at the head and tail of
 * {@code EMFModelPartRoot#animate}; EMF 3.3 exposes the same two moments as hooks.</p>
 */
public final class TakeASeatAnimationHook extends EMFAnimationApi.EMFAnimationHook {

    private static final String POSE_SOURCE = "takeaseat";

    private static final ThreadLocal<Map<EMFModelPartVanilla, PoseSnapshot>> SNAPSHOTS =
            ThreadLocal.withInitial(HashMap::new);

    private TakeASeatAnimationHook() {
    }

    public static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new TakeASeatAnimationHook());
        } catch (Throwable t) {
            System.err.println("[EMF Compat: Take a Seat] could not register the EMF animation hook: " + t);
        }
    }

    @Override
    public boolean onAnimationStart(AnimationContext context, boolean isCancelledByHook) {
        // Never cancels the animation: the head is supposed to keep playing it.
        SNAPSHOTS.get().clear();

        if (!TakeASeatEMFCompatClient.isEnabled()) return true;
        if (!FabricLoader.getInstance().isModLoaded("seatify")) return true;

        EMFEntityRenderState state = context.activeState();
        if (state == null || state.isFirstPersonHand()) return true;
        if (!(state.emfEntity() instanceof Entity entity)) return true;
        if (!(entity instanceof AbstractClientPlayer player)) return true;

        IAnimation layer;
        try {
            layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, SeatifyClient.SIT_LAYER);
        } catch (Exception e) {
            return true;
        }
        if (layer == null || !layer.isActive()) {
            PoseManager.clearPoses(player.getUUID(), POSE_SOURCE);
            return true;
        }

        EMFModelPartRoot root = context.animatingModelRoot();
        if (!root.modelName.toString().startsWith("player")) return true;

        Map<EMFModelPartVanilla, PoseSnapshot> snaps = SNAPSHOTS.get();
        Map<String, PoseSnapshot> savedParts = new HashMap<>();
        for (Map.Entry<String, EMFModelPartVanilla> entry : root.getAllVanillaPartsByNameEMF().entrySet()) {
            String name = entry.getKey();
            PoseSnapshot snap = new PoseSnapshot(entry.getValue());
            savedParts.put(name, snap);
            if ("head".equals(name) || "hat".equals(name)) continue;
            snaps.put(entry.getValue(), snap);
        }

        if (!savedParts.isEmpty()) {
            PoseManager.savePoses(
                    player.getUUID(),
                    POSE_SOURCE,
                    new SavedPoses(null, null, savedParts),
                    false
            );
        }
        return true;
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        if (!TakeASeatEMFCompatClient.isEnabled()) return;
        Map<EMFModelPartVanilla, PoseSnapshot> snaps = SNAPSHOTS.get();
        if (snaps.isEmpty()) return;

        snaps.forEach((part, snap) -> snap.apply(part));
        snaps.clear();
    }
}
