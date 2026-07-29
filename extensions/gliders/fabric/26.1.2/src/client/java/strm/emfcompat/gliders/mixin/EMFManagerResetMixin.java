package strm.emfcompat.gliders.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.gliders.compat.FlightAnimationSupport;
import traben.entity_model_features.EMFManager;

/**
 * Resets the flight-animation detection on every EMF reload, so switching to a
 * pack without a flight animation correctly disables the paragliding spoof.
 */
@Mixin(EMFManager.class)
public class EMFManagerResetMixin {

    @Inject(method = "resetInstance", at = @At("HEAD"), remap = false)
    private static void gliders$resetFlightDetection(CallbackInfo ci) {
        FlightAnimationSupport.reset();
    }
}
