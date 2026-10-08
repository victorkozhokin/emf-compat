package strm.emfcompat.carryon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
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

    @Inject(
            method = "draw(Lnet/minecraft/world/entity/player/Player;Lcom/mojang/blaze3d/vertex/PoseStack;IFLnet/minecraft/client/renderer/SubmitNodeCollector;Z)Z",
            at = @At("RETURN")
    )
    private static void emfcompat$finishCarriedRender(
            Player player, PoseStack poseStack, int packedLight, float partialTicks,
            SubmitNodeCollector collector, boolean renderItem, CallbackInfoReturnable<Boolean> cir
    ) {
        CarryOnRenderState.clearCarriedEntities();
    }

    /**
     * The mob exactly as Carry On is about to draw it, in first person and in third.
     *
     * <p>In first person Carry On never reaches {@code applyEntityTransformations}, where the
     * carried mob is otherwise marked ({@link CarryRenderHelperMixin}), so the mob went unmarked:
     * no Frozen pose and a clock stuck at 0. And Carry On builds a new copy of the mob each time it
     * is asked for one, so only the copy handed to the renderer is the one on screen - this is it,
     * already moved and turned by Carry On.</p>
     */
    @ModifyArg(
            method = "drawEntity(Lnet/minecraft/world/entity/player/Player;Lcom/mojang/blaze3d/vertex/PoseStack;IFLnet/minecraft/client/renderer/SubmitNodeCollector;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"),
            index = 0
    )
    private static Entity emfcompat$markDrawnEntity(Entity entity) {
        CarryOnRenderState.markCarried(entity);
        CarryOnRenderState.stabilizeAnimated(entity);
        return entity;
    }
}
