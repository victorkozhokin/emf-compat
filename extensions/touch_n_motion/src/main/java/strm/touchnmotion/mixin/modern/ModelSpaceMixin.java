package strm.touchnmotion.mixin.modern;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.touchnmotion.ModelSpace;
import strm.touchnmotion.create.ejector.EjectorLaunch;
import strm.touchnmotion.interaction.DrawnEntities;

/**
 * Before the model is animated, from 1.21.11 on: the renderer hands the model on to be drawn
 * later, with the pose stack as it is here - turned, flipped, scaled and lifted into the model's
 * space - and animates it for its layers right after, and again when it is drawn
 * ({@link ModelSpace}). The entity is not in hand, only its render state ({@link DrawnEntities}).
 */
@Mixin(LivingEntityRenderer.class)
public class ModelSpaceMixin {

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;isBodyVisible(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z"))
    private void touchnmotion$beforeAnimating(LivingEntityRenderState state, PoseStack stack, SubmitNodeCollector collector,
                                              CameraRenderState camera, CallbackInfo ci) {
        LivingEntity entity = DrawnEntities.of(state);
        if (entity == null) return;
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        ModelSpace.before(entity, stack, partialTick, player -> {
            if (EjectorLaunch.wantsCrouch(player) && state instanceof HumanoidRenderState humanoid && !humanoid.isCrouching) {
                humanoid.isCrouching = true;
                EjectorLaunch.dropForCrouch(stack);
            }
        });
    }
}
