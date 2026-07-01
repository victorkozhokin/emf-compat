package strm.emfcompat.carryon.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.carryon.CarryOnRenderState;
import tschipp.carryon.client.render.CarriedObjectRender;

/**
 * Clears the carried-entity tracking set at the start of each Carry On
 * third-person render pass. Individual entities are added in
 * {@link CarryRenderHelperMixin} at the start of
 * {@code applyEntityTransformations}.
 */
@Mixin(CarriedObjectRender.class)
public class CarriedObjectRenderMixin {

    @Inject(
            method = "drawThirdPerson(FLorg/joml/Matrix4f;)V",
            at = @At("HEAD")
    )
    private static void emfcompat$clearCarriedEntities(
            float partialTicks,
            Matrix4f matrix,
            CallbackInfo ci
    ) {
        CarryOnRenderState.clearCarriedEntities();
    }
}
