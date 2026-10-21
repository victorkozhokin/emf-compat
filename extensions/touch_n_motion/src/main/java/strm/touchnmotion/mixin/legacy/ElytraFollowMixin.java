package strm.touchnmotion.mixin.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.touchnmotion.cape.CapeAndElytra;

/**
 * Up to 1.21.10: an elytra on a player is drawn carried with what was done to the torso
 * ({@link CapeAndElytra}), whether a pack animates the wings or not.
 */
@Mixin(ElytraLayer.class)
public abstract class ElytraFollowMixin {
    @Unique
    private boolean touchnmotion$carried;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"))
    private void touchnmotion$carry(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity,
                                    float swing, float swingAmount, float partial, float age, float headYaw, float headPitch, CallbackInfo ci) {
        touchnmotion$carried = entity instanceof Player player && CapeAndElytra.beginLayer(pose, player.getUUID());
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("RETURN"))
    private void touchnmotion$uncarry(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity,
                                      float swing, float swingAmount, float partial, float age, float headYaw, float headPitch, CallbackInfo ci) {
        CapeAndElytra.endLayer(pose, touchnmotion$carried);
        touchnmotion$carried = false;
    }
}
