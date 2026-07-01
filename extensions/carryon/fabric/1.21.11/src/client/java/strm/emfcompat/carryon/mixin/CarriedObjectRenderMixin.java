package strm.emfcompat.carryon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.carryon.CarryOnRenderState;
import tschipp.carryon.client.render.CarriedObjectRender;

/**
 * Clears the carried-entity tracking set at the start of each Carry On
 * render pass. Individual entities are added in
 * {@link CarryRenderHelperMixin} at the start of
 * {@code applyEntityTransformations}.
 */
@Mixin(CarriedObjectRender.class)
public class CarriedObjectRenderMixin {

    @Inject(
            method = "draw(Lnet/minecraft/world/entity/player/Player;Lcom/mojang/blaze3d/vertex/PoseStack;IFLnet/minecraft/client/renderer/SubmitNodeCollector;Z)Z",
            at = @At("HEAD")
    )
    private static void emfcompat$clearCarriedEntities(
            Player player,
            PoseStack poseStack,
            int packedLight,
            float partialTicks,
            SubmitNodeCollector collector,
            boolean renderItem,
            CallbackInfoReturnable<Boolean> cir
    ) {
        CarryOnRenderState.clearCarriedEntities();
    }
}
