package strm.emfcompat.create.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.create.flight.FlightAnimationSupport;
import strm.emfcompat.create.flight.FlightCompat;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;

/**
 * Suppresses the vanilla {@code is_jumping} animation variable while the animated player flies
 * a Create-ecosystem jetpack. Jetpack thrust is bound to the jump key, so without this the pack
 * keeps blending its jump animation (at half strength) into the flight pose.
 *
 * <p>Applies only while airborne with an active jetpack, so ground jumps keep their animation
 * and the fall/landing chain (which does not depend on {@code is_jumping}) is unaffected.</p>
 */
@Mixin(EMFAnimationEntityContext.class)
public class FlightJumpSuppressMixin {

    @Inject(method = "isJumping()Z", at = @At("RETURN"), cancellable = true, remap = false)
    private static void emfcompat$suppressJumpWhileJetpacking(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && FlightAnimationSupport.isPackFlightAware()
                && FlightCompat.isCurrentEmfEntityJetpackFlying()) {
            cir.setReturnValue(false);
        }
    }
}
