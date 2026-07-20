package strm.emfcompat.takeaseat.mixin;

import com.takeaseat.client.TakeASeatClient;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseSnapshot;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;
import traben.entity_model_features.utils.EMFEntity;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = EMFModelPartRoot.class, priority = 900)
public class EMFModelPartRootMixin {

    private static final ThreadLocal<Map<EMFModelPartVanilla, PoseSnapshot>> TAKEASEAT$SNAPSHOTS =
            ThreadLocal.withInitial(HashMap::new);

    @Inject(method = "animate", at = @At("HEAD"))
    private void takeaseat$capturePoses(CallbackInfo ci) {
        if (!ModList.get().isLoaded("takeaseat")) return;
        if (EMFAnimationEntityContext.isFirstPersonHand) return;

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
        if (layer == null || !layer.isActive()) return;

        EMFModelPartRoot root = (EMFModelPartRoot) (Object) this;
        if (!root.modelName.toString().startsWith("player")) return;

        Map<EMFModelPartVanilla, PoseSnapshot> snaps = TAKEASEAT$SNAPSHOTS.get();
        snaps.clear();

        for (EMFModelPartVanilla part : root.getAllVanillaPartsEMF()) {
            String name = takeaseat$partName(part);
            if ("head".equals(name) || "hat".equals(name)) continue;
            snaps.put(part, new PoseSnapshot(part));
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
