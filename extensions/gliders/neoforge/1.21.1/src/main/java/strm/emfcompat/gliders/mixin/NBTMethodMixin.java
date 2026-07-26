package strm.emfcompat.gliders.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.gliders.compat.FlightAnimationSupport;
import strm.emfcompat.gliders.compat.GlidingState;
import traben.entity_model_features.models.animation.math.methods.emf.NBTMethod;

/**
 * Makes EMF's {@code nbt(abilities.flying, ...)} read as creative-flight while the animated
 * player is gliding with any supported glider mod. This lets resource packs with creative-flight animations
 * (FA+ Player) play them during gliding without touching the real NBT, the server
 * or the physics. Only active when the loaded pack actually has a flight animation
 * (see {@link FlightAnimationSupport}).
 *
 * <p>This injection covers the ASM-compiled animation path, which calls the static helper
 * directly. For the lambda fallback see {@link NBTMethodLambdaMixin}.</p>
 */
@Mixin(NBTMethod.class)
public class NBTMethodMixin {

    @Inject(method = "nbtMethodStatic", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gliders$spoofGliding(String key, String expected, CallbackInfoReturnable<Boolean> cir) {
        if (!"abilities.flying".equals(key.trim())) return;
        if (!FlightAnimationSupport.isPackFlightAware()) return;
        if (GlidingState.isCurrentEmfEntityGliding()) {
            // Pretend abilities.flying == 1: true only when the animation expects "1".
            cir.setReturnValue("1".equals(expected.trim()));
        }
    }
}
