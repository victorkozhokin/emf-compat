package strm.emfcompat.animationadditions.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.animationadditions.blockuse.BlockUse;
import strm.emfcompat.animationadditions.footgrounding.compat.FootGrounding;
import strm.emfcompat.animationadditions.footgrounding.compat.HorseFootGrounding;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.motion.MotionRuntime;
import strm.emfcompat.animationadditions.ejector.EjectorLaunch;
import strm.emfcompat.animationadditions.torso.TorsoLean;
import strm.emfcompat.animationadditions.wallhand.WallSqueeze;
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
        strm.emfcompat.animationadditions.blockuse.CockpitControls.orient(player, stack);
        emfcompat$quietSwing(player);
        EjectorLaunch.crouch(player, ((LivingEntityRenderer<?, ?>) (Object) this).getModel(), stack);
        FootGrounding.modelPose(player, stack);
        IKFrame frame = IKFrame.capture(stack.last().pose(),
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        strm.emfcompat.animationadditions.blockuse.CockpitControls.frame(player,frame);
        MotionRuntime.modelPose(player);
        InteractionRuntime.modelPose(player, frame);
        EjectorLaunch.modelPose(player);
        WallSqueeze.modelPose(player, frame);
        TorsoLean.modelPose(player);
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
