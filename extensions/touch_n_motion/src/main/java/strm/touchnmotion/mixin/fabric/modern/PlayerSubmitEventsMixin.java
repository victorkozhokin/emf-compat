package strm.touchnmotion.mixin.fabric.modern;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.touchnmotion.footgrounding.FootGroundingFeature;
import strm.touchnmotion.horsesync.ClientEventHandler;
import strm.touchnmotion.interaction.DrawnEntities;

/**
 * A player about to be drawn, and drawn, from 1.21.11 on: the rider is lifted with an animated
 * horse and then, innermost, lowered and pitched with a horse that stands on uneven ground - and
 * both undone in the opposite order. The same as {@code mixin/fabric/legacy/PlayerRenderEventsMixin}
 * does before; here around the handing of the model on to be drawn, which is when the stack counts.
 */
@Mixin(LivingEntityRenderer.class)
public class PlayerSubmitEventsMixin {

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("HEAD"))
    private void touchnmotion$beforePlayer(LivingEntityRenderState state, PoseStack stack, SubmitNodeCollector collector,
                                           CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState)) return;
        LivingEntity entity = DrawnEntities.of(state);
        if (!(entity instanceof Player player)) return;
        ClientEventHandler.onRenderPlayerPre(player, stack);
        FootGroundingFeature.onRenderPlayerPre(player, stack, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("RETURN"))
    private void touchnmotion$afterPlayer(LivingEntityRenderState state, PoseStack stack, SubmitNodeCollector collector,
                                          CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState)) return;
        LivingEntity entity = DrawnEntities.of(state);
        if (!(entity instanceof Player player)) return;
        FootGroundingFeature.onRenderPlayerPost(stack);
        ClientEventHandler.onRenderPlayerPost(player, stack);
    }
}
