package strm.emfcompat.animationadditions.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.animationadditions.footgrounding.compat.FootGrounding;
import strm.emfcompat.animationadditions.footgrounding.compat.HorseFootGrounding;
import strm.emfcompat.animationadditions.lookat.LookAt;
import strm.emfcompat.animationadditions.plantreach.PlantReach;
import strm.emfcompat.animationadditions.wallhand.WallHand;
import strm.emfcompat.core.ik.IKFrame;

/**
 * Right before the model is animated the pose stack is exactly the model's space: every feature
 * looks at the world from there. Foot grounding goes first, because it lowers the model on this
 * same stack and the others aim from where the model is really drawn.
 */
@Mixin(LivingEntityRenderer.class)
public class AnimationAdditionsRenderMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void emfcompat$animationAdditionsBeforeAnimating(LivingEntity entity, float yaw, float partialTick,
                                                            PoseStack stack, MultiBufferSource buffers, int light,
                                                            CallbackInfo ci) {
        if (HorseFootGrounding.handles(entity)) {
            HorseFootGrounding.modelPose((AbstractHorse) entity, stack, partialTick);
            return;
        }
        if (!(entity instanceof AbstractClientPlayer player)) return;
        FootGrounding.modelPose(player, stack);
        IKFrame frame = IKFrame.capture(stack.last().pose(),
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        // The wall first: an arm on a wall is not reached out to plants.
        WallHand.modelPose(player, frame);
        PlantReach.modelPose(player, frame);
        LookAt.modelPose(player, frame);
    }
}
