package strm.emfcompat.create.mixin;

import com.simibubi.create.foundation.render.PlayerSkyhookRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.create.EMFCompatCreateMod;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps Create's skyhook hang pose on the player while EMF is active.
 *
 * <p>Create 6.x poses the whole model (head, body, both arms, both legs) in
 * {@code afterSetupAnim}; EMF then re-animates the model from the resource pack, which slides
 * the parts it touches back toward the pack's idle while the rest keep the hang pose — the
 * "half the body slides off" symptom. Instead of pausing EMF / forcing the vanilla model (the
 * legacy approach, fragile with FreshAnimations), we capture the full hang pose here and let
 * the Core mixins restore it after EMF runs — the same capture-and-restore path the Aeronautics
 * handle uses.</p>
 */
@Mixin(value = PlayerSkyhookRenderer.class, remap = false)
public class PlayerSkyhookRendererMixin {

    private static final String SOURCE = "create_skyhook";

    @Inject(method = "afterSetupAnim", at = @At("RETURN"))
    private static void emfcompatCreate$onAfterSetupAnim(Player player, HumanoidModel<?> model, CallbackInfo ci) {
        if (player == null || model == null) return;
        if (Minecraft.getInstance().isPaused()) return;

        if (!EMFCompatCreateMod.isEnabled() || !EMFCompatCreateMod.isSkyhook()) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
            return;
        }

        Set<UUID> hangingPlayers = PlayerSkyhookRendererAccessor.emfcompatCreate$getHangingPlayers();
        if (hangingPlayers == null || !hangingPlayers.contains(player.getUUID())) {
            PoseManager.clearPoses(player.getUUID(), SOURCE);
            return;
        }

        // Skyhook rigidly poses the whole body, so capture every part and restore it absolutely.
        // Hat / sleeves / pants / jacket follow their parent parts in the Core restore.
        Map<String, PoseSnapshot> parts = new HashMap<>();
        parts.put("head", new PoseSnapshot(model.head));
        parts.put("body", new PoseSnapshot(model.body));
        parts.put("left_arm", new PoseSnapshot(model.leftArm));
        parts.put("right_arm", new PoseSnapshot(model.rightArm));
        parts.put("left_leg", new PoseSnapshot(model.leftLeg));
        parts.put("right_leg", new PoseSnapshot(model.rightLeg));

        PoseManager.savePoses(player.getUUID(), SOURCE, null, null, parts);
    }

    @Inject(method = "updatePlayerList", at = @At("RETURN"))
    private static void emfcompatCreate$onUpdatePlayerList(Collection<UUID> uuids, CallbackInfo ci) {
        PoseManager.retainOnly(uuids, SOURCE);
    }
}
