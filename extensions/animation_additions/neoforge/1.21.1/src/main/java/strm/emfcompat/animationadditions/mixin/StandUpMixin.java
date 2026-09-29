package strm.emfcompat.animationadditions.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.animationadditions.blockuse.BlockUse;

/**
 * Marks the player drawn standing up though crouching ({@link BlockUse#standsUp}) for the length
 * of their draw, so {@link CrouchingMixin} tells the renderer, the model and the pack they are
 * not. Nothing else sees it: the game's own crouching is untouched outside the draw.
 */
@Mixin(EntityRenderDispatcher.class)
public class StandUpMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private <E extends Entity> void emfcompat$standUpStart(E entity, double x, double y, double z, float yaw, float partialTick,
                                                          PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        BlockUse.drawnStanding = entity instanceof Player player && BlockUse.standsUp(player.getUUID()) ? entity : null;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <E extends Entity> void emfcompat$standUpEnd(E entity, double x, double y, double z, float yaw, float partialTick,
                                                        PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        BlockUse.drawnStanding = null;
    }
}
