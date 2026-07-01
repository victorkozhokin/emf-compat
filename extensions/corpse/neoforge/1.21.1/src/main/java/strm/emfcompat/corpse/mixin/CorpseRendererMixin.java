package strm.emfcompat.corpse.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.maxhenkel.corpse.entities.CorpseEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.corpse.CorpseTracker;

/**
 * Hooks into the Corpse renderer to flag the current render pass.
 *
 * <p>The actual forcing of the vanilla EMF variant is done in
 * {@link LivingEntityRendererMixin}, because the dummy player/skeleton is rendered
 * through the regular player/skeleton renderer and we need to wrap that renderer,
 * not the Corpse renderer itself.</p>
 */
@Mixin(value = de.maxhenkel.corpse.entities.CorpseRenderer.class, remap = false)
public class CorpseRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void emfcompatCorpse$onRenderStart(CorpseEntity entity, float entityYaw, float partialTicks,
                                               PoseStack matrixStack, MultiBufferSource buffer, int packedLight,
                                               CallbackInfo ci) {
        CorpseTracker.LOGGER.debug("[EMFCompat:Corpse] CorpseRenderer.render START for entity {}",
                entity.getUUID());
        CorpseTracker.setRenderingCorpse(true);
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void emfcompatCorpse$onRenderEnd(CorpseEntity entity, float entityYaw, float partialTicks,
                                             PoseStack matrixStack, MultiBufferSource buffer, int packedLight,
                                             CallbackInfo ci) {
        CorpseTracker.setRenderingCorpse(false);
        CorpseTracker.LOGGER.debug("[EMFCompat:Corpse] CorpseRenderer.render END for entity {}",
                entity.getUUID());
    }
}
