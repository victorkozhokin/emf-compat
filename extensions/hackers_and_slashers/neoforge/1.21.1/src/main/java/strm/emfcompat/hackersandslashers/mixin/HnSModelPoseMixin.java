package strm.emfcompat.hackersandslashers.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.hackersandslashers.EMFCompatHnSMod;
import strm.emfcompat.hackersandslashers.compat.HnSHeadLook;

/**
 * Right before the model is animated the pose stack is exactly the model's space, with the torso
 * twist the animation library put on it in {@code setupRotations}. The head aim needs that space.
 */
@Mixin(LivingEntityRenderer.class)
public class HnSModelPoseMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void emfcompat$hnsBeforeAnimating(LivingEntity entity, float yaw, float partialTick, PoseStack stack,
                                              MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player && EMFCompatHnSMod.isEnabled()
                && EMFCompatHnSMod.isHeadLook()) {
            HnSHeadLook.modelPose(player, stack.last().pose());
        }
    }
}
