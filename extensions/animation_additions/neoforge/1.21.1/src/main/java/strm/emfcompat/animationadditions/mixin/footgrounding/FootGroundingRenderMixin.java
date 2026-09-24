package strm.emfcompat.animationadditions.mixin.footgrounding;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.animationadditions.footgrounding.compat.FootGrounding;

/**
 * Right before the model is animated the pose stack is exactly the model's space: the feet are
 * looked for from there, and the model is lowered on the same stack, which the draw uses next.
 */
@Mixin(LivingEntityRenderer.class)
public class FootGroundingRenderMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void emfcompat$footGroundingBeforeAnimating(LivingEntity entity, float yaw, float partialTick,
                                                       PoseStack stack, MultiBufferSource buffers, int light,
                                                       CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player) {
            FootGrounding.modelPose(player, stack);
        }
    }
}
