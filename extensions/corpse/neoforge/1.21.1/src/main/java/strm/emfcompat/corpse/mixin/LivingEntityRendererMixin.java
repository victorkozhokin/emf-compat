package strm.emfcompat.corpse.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.corpse.CorpseTracker;

/**
 * Forces the vanilla EMF model variant while a living entity is rendered inside the
 * Corpse render pass.
 *
 * <p>Corpse draws its dummy player/skeleton by invoking the regular player/skeleton
 * renderer. That renderer shares its EMF model with the real player, so we temporarily
 * switch the model to variant {@code 0} (vanilla) for the duration of the dummy render
 * and restore it afterwards.</p>
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD")
    )
    private void emfcompatCorpse$onLivingRenderStart(LivingEntity entity, float entityYaw, float partialTicks,
                                                     PoseStack matrixStack, MultiBufferSource buffer, int packedLight,
                                                     CallbackInfo ci) {
        if (!CorpseTracker.isRenderingCorpse()) {
            return;
        }
        CorpseTracker.pushVanillaModelState((EntityRenderer<?>) (Object) this);
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN")
    )
    private void emfcompatCorpse$onLivingRenderEnd(LivingEntity entity, float entityYaw, float partialTicks,
                                                   PoseStack matrixStack, MultiBufferSource buffer, int packedLight,
                                                   CallbackInfo ci) {
        if (!CorpseTracker.isRenderingCorpse()) {
            return;
        }
        CorpseTracker.popVanillaModelState((EntityRenderer<?>) (Object) this);
    }
}
