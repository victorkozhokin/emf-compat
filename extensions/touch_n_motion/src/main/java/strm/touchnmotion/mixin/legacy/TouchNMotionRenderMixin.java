package strm.touchnmotion.mixin.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.touchnmotion.blockuse.BlockUse;
import strm.touchnmotion.create.ejector.EjectorLaunch;

/** Before the model is animated, up to 1.21.10: the renderer animates and draws at once, the entity in hand ({@link strm.touchnmotion.ModelSpace}). */
@Mixin(LivingEntityRenderer.class)
public class TouchNMotionRenderMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void emfcompat$animationAdditionsBeforeAnimating(LivingEntity entity, float yaw, float partialTick,
                                                            PoseStack stack, MultiBufferSource buffers, int light,
                                                            CallbackInfo ci) {
        strm.touchnmotion.ModelSpace.before(entity, stack, partialTick, player -> {
            emfcompat$quietSwing(player);
            if (EjectorLaunch.wantsCrouch(player)
                    && ((LivingEntityRenderer<?, ?>) (Object) this).getModel() instanceof net.minecraft.client.model.HumanoidModel<?> humanoid
                    && !humanoid.crouching) {
                humanoid.crouching = true;
                EjectorLaunch.dropForCrouch(stack);
            }
        });
    }

    /** The swing kept off a player this draw, to put back after it: whose, and the values. */
    @Unique
    private AbstractClientPlayer emfcompat$quieted;
    @Unique
    private float emfcompat$attackAnim, emfcompat$oAttackAnim;

    /**
     * A hand crank held down starts a swing on every repeat of the click, and the pack plays each
     * in full, twisting the torso and the other arm. While the hand is on it the swing is kept off
     * the body for this draw: the model's swing and the entity's, which the pack reads, are zeroed
     * and put back when the draw is done.
     */
    @Unique
    private void emfcompat$quietSwing(AbstractClientPlayer player) {
        if (!BlockUse.quietsSwing(player.getUUID())) return;
        emfcompat$quieted = player;
        emfcompat$attackAnim = player.attackAnim;
        emfcompat$oAttackAnim = player.oAttackAnim;
        player.attackAnim = 0f;
        player.oAttackAnim = 0f;
        ((LivingEntityRenderer<?, ?>) (Object) this).getModel().attackTime = 0f;
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void emfcompat$restoreSwing(LivingEntity entity, float yaw, float partialTick, PoseStack stack,
                                        MultiBufferSource buffers, int light, CallbackInfo ci) {
        AbstractClientPlayer player = emfcompat$quieted;
        if (player == null) return;
        emfcompat$quieted = null;
        player.attackAnim = emfcompat$attackAnim;
        player.oAttackAnim = emfcompat$oAttackAnim;
    }
}
