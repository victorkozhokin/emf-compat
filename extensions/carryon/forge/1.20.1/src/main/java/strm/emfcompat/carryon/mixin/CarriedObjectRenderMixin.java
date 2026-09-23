package strm.emfcompat.carryon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.carryon.CarryOnRenderState;
import tschipp.carryon.client.render.CarriedObjectRender;
import tschipp.carryon.client.render.CarryRenderHelper;

/**
 * Clears the carried-entity tracking set at the start of each Carry On
 * third-person render pass. Individual entities are added in
 * {@link CarryRenderHelperMixin} at the start of
 * {@code applyEntityTransformations}.
 */
@Mixin(value = CarriedObjectRender.class, remap = false)
public class CarriedObjectRenderMixin {

    @Inject(
            method = "drawThirdPerson(FLcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD")
    )
    private static void emfcompat$clearCarriedEntities(
            float partialTicks,
            PoseStack poseStack,
            CallbackInfo ci
    ) {
        CarryOnRenderState.clearCarriedEntities();
    }

    @Inject(method = "drawThirdPerson(FLcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("RETURN"))
    private static void emfcompat$finishThirdPerson(float partialTicks, PoseStack poseStack, CallbackInfo ci) {
        CarryOnRenderState.clearCarriedEntities();
    }

    @Inject(
            method = "drawFirstPersonEntity(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;IF)V",
            at = @At("HEAD")
    )
    private static void emfcompat$beginFirstPersonEntity(Player player, MultiBufferSource buffers,
                                                          PoseStack poseStack, int light,
                                                          float partialTicks, CallbackInfo ci) {
        CarryOnRenderState.clearCarriedEntities();
    }

    /**
     * The mob exactly as Carry On is about to draw it in first person.
     *
     * <p>Carry On builds a new copy of the mob every time it is asked for one, so asking for it
     * here ({@code CarryRenderHelper.getRenderEntity}) marked and stabilised a copy nobody drew:
     * the one on screen kept a clock of 0 and never animated. The argument of the draw call is the
     * copy that is drawn, already moved and turned by Carry On.</p>
     */
    @ModifyArg(
            method = "drawFirstPersonEntity(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;IF)V",
            // The class is remap = false for Carry On's own methods; the draw call is Minecraft's
            // and has an SRG name at runtime, so this one target has to be remapped.
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", remap = true),
            index = 0
    )
    private static Entity emfcompat$markFirstPersonEntity(Entity entity) {
        CarryOnRenderState.markCarried(entity);
        CarryOnRenderState.stabilizeAnimated(entity);
        return entity;
    }

    @Inject(
            method = "drawFirstPersonEntity(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;IF)V",
            at = @At("RETURN")
    )
    private static void emfcompat$finishFirstPersonEntity(Player player, MultiBufferSource buffers,
                                                           PoseStack poseStack, int light,
                                                           float partialTicks, CallbackInfo ci) {
        CarryOnRenderState.clearCarriedEntities();
    }
}
