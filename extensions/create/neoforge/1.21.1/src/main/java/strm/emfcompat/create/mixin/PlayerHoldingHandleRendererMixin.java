package strm.emfcompat.create.mixin;

import dev.simulated_team.simulated.content.blocks.handle.PlayerHoldingHandleRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

@Mixin(value = PlayerHoldingHandleRenderer.class, remap = false)
public class PlayerHoldingHandleRendererMixin {

    private static final String SOURCE = "create_handle";

    @Inject(method = "afterSetupAnim", at = @At("RETURN"))
    private static void emfcompatCreate$onAfterSetupAnim(Player player, HumanoidModel<?> model, CallbackInfo ci) {
        if (player == null || model == null) return;
        if (Minecraft.getInstance().isPaused()) return;

        Set<UUID> holdingPlayers = PlayerHoldingHandleRendererAccessor.emfcompatCreate$getHoldingPlayers();
        if (holdingPlayers == null || !holdingPlayers.contains(player.getUUID())) {
            return;
        }

        PoseManager.savePoses(
                player.getUUID(),
                SOURCE,
                new PoseSnapshot(model.leftArm),
                new PoseSnapshot(model.rightArm)
        );
    }

    @Inject(method = "updatePlayerList", at = @At("RETURN"))
    private static void emfcompatCreate$onUpdatePlayerList(Collection<UUID> uuids, CallbackInfo ci) {
        PoseManager.retainOnly(uuids, SOURCE);
    }
}
