package strm.emfcompat.takeaseat.mixin;

import com.takeaseat.client.TakeASeatClient;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.core.SavedPoses;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;
import traben.entity_model_features.utils.EMFEntity;

import java.util.HashMap;
import java.util.Map;

@Mixin(EMFModelPartRoot.class)
public class EMFModelPartRootMixin {

    private static final String TAKEASEAT$POSE_SOURCE = "takeaseat";
    private static final ThreadLocal<Map<EMFModelPartVanilla, PoseSnapshot>> TAKEASEAT$SNAPSHOTS = ThreadLocal.withInitial(HashMap::new);

    @Inject(method = "animate", at = @At("HEAD"))
    private void takeaseat$capturePoses(CallbackInfo ci) {
        if (!FabricLoader.getInstance().isModLoaded("takeaseat")) return;

        EMFEntity emfEntity = EMFAnimationApi.getCurrentEntity();
        if (emfEntity == null || emfEntity.etf$isBlockEntity()) return;

        Entity entity = (Entity) emfEntity;
        if (!(entity instanceof AbstractClientPlayer player)) return;

        IAnimation layer;
        try {
            layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, TakeASeatClient.SIT_LAYER);
        } catch (Exception e) {
            return;
        }
        if (layer == null || !layer.isActive()) {
            PoseManager.clearPoses(player.getUUID(), TAKEASEAT$POSE_SOURCE);
            return;
        }

        EMFModelPartRoot root = (EMFModelPartRoot) (Object) this;
        if (!root.modelName.toString().startsWith("player")) return;

        Map<EMFModelPartVanilla, PoseSnapshot> snaps = TAKEASEAT$SNAPSHOTS.get();
        snaps.clear();

        Map<String, PoseSnapshot> savedParts = new HashMap<>();
        for (EMFModelPartVanilla part : root.getAllVanillaPartsEMF()) {
            String name = takeaseat$partName(part);
            PoseSnapshot snap = new PoseSnapshot(part);
            savedParts.put(name, snap);
            if ("head".equals(name) || "hat".equals(name)) continue;
            snaps.put(part, snap);
        }

        if (!savedParts.isEmpty()) {
            PoseManager.savePoses(
                    player.getUUID(),
                    TAKEASEAT$POSE_SOURCE,
                    new SavedPoses(null, null, savedParts),
                    false
            );
        }
    }

    @Inject(method = "animate", at = @At("TAIL"))
    private void takeaseat$restorePoses(CallbackInfo ci) {
        Map<EMFModelPartVanilla, PoseSnapshot> snaps = TAKEASEAT$SNAPSHOTS.get();
        if (snaps.isEmpty()) return;

        snaps.forEach((part, snap) -> snap.apply(part));
        snaps.clear();
    }

    private static String takeaseat$partName(EMFModelPartVanilla part) {
        String s = part.toStringShort();
        int start = s.indexOf("part ");
        if (start == -1) return "";
        start += 5;
        int end = s.length() - 1;
        if (end < start) return "";
        return s.substring(start, end);
    }
}
