package strm.touchnmotion.mixin.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.touchnmotion.cape.CapeAndElytra;

/**
 * The cape's layer, up to 1.21.10 ({@link CapeAndElytra}): it is drawn carried with what was done to
 * the torso - whether the game's layer draws it or EMF a pack's cape model in its place; and it swings
 * as {@code CapeLayer} reckons it from the body's yaw, where a model turned round from that body (to a
 * train's controls, a wheel) would have it swing across the way it goes - while the layer draws, the
 * body is the one the model is drawn with, and back after.
 */
@Mixin(CapeLayer.class)
public abstract class CapeTurnMixin {
    @Unique
    private float touchnmotion$turned;
    @Unique
    private boolean touchnmotion$carried;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V", at = @At("HEAD"))
    private void touchnmotion$turn(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                   float swing, float swingAmount, float partial, float age, float headYaw, float headPitch, CallbackInfo ci) {
        touchnmotion$carried = CapeAndElytra.beginLayer(pose, player.getUUID());
        touchnmotion$turned = CapeAndElytra.turned(player.getUUID());
        player.yBodyRot += touchnmotion$turned;
        player.yBodyRotO += touchnmotion$turned;
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V", at = @At("RETURN"))
    private void touchnmotion$unturn(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                     float swing, float swingAmount, float partial, float age, float headYaw, float headPitch, CallbackInfo ci) {
        player.yBodyRot -= touchnmotion$turned;
        player.yBodyRotO -= touchnmotion$turned;
        touchnmotion$turned = 0;
        CapeAndElytra.endLayer(pose, touchnmotion$carried);
        touchnmotion$carried = false;
    }
}
