package strm.touchnmotion.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.touchnmotion.footgrounding.FootGroundingFeature;
import strm.touchnmotion.horsesync.ClientEventHandler;

/**
 * A player about to be drawn, and drawn: what NeoForge tells by its render-player events. The
 * rider is lifted with an animated horse and then, innermost, lowered and pitched with a horse
 * that stands on uneven ground - and both undone in the opposite order.
 */
@Mixin(PlayerRenderer.class)
public class PlayerRenderEventsMixin {

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void touchnmotion$beforePlayer(AbstractClientPlayer player, float yaw, float partialTick, PoseStack stack,
                                           MultiBufferSource buffers, int light, CallbackInfo ci) {
        ClientEventHandler.onRenderPlayerPre(player, stack);
        FootGroundingFeature.onRenderPlayerPre(player, stack, partialTick);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void touchnmotion$afterPlayer(AbstractClientPlayer player, float yaw, float partialTick, PoseStack stack,
                                          MultiBufferSource buffers, int light, CallbackInfo ci) {
        FootGroundingFeature.onRenderPlayerPost(stack);
        ClientEventHandler.onRenderPlayerPost(player, stack);
    }
}
