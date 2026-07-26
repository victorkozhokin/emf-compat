package strm.emfcompat.create.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.create.flight.FlightAnimationSupport;
import strm.emfcompat.create.flight.FlightCompat;
import traben.entity_model_features.models.animation.math.methods.emf.NBTMethod;

/**
 * Makes EMF's {@code nbt(abilities.flying, ...)} read as creative-flight while the animated
 * player flies a Create-ecosystem jetpack (Cosmonautics or Stuff 'N Additions). Lets resource
 * packs with creative-flight animations (FA+ Player) play them during jetpack flight without
 * touching the real NBT, the server or the physics.
 *
 * <p>This injection covers the ASM-compiled animation path; for the lambda fallback see
 * {@link FlightNbtMethodLambdaMixin}.</p>
 */
@Mixin(NBTMethod.class)
public class FlightNbtMethodMixin {

    @Inject(method = "nbtMethodStatic", at = @At("HEAD"), cancellable = true, remap = false)
    private static void emfcompat$spoofJetpackFlying(String key, String expected, CallbackInfoReturnable<Boolean> cir) {
        if (!"abilities.flying".equals(key.trim())) return;
        if (!FlightAnimationSupport.isPackFlightAware()) return;
        if (FlightCompat.isCurrentEmfEntityJetpackFlying()) {
            cir.setReturnValue("1".equals(expected.trim()));
        }
    }
}
