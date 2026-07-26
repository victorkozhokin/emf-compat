package strm.emfcompat.create.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.create.flight.FlightAnimationSupport;
import strm.emfcompat.create.flight.FlightCompat;
import traben.entity_model_features.models.animation.AnimSetupContext;
import traben.entity_model_features.models.animation.math.expression_tree.MathValue;
import traben.entity_model_features.models.animation.math.methods.emf.NBTMethod;

import java.util.List;

/**
 * Lambda-evaluation counterpart of {@link FlightNbtMethodMixin}: if an animation containing
 * {@code nbt(abilities.flying, ...)} falls back to supplier-based evaluation instead of the
 * ASM-compiled path, wrap the supplier with the same jetpack spoof.
 */
@Mixin(NBTMethod.class)
public class FlightNbtMethodLambdaMixin {

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void emfcompat$wrapFlyingSupplier(List<String> args, boolean isInverted, AnimSetupContext context, CallbackInfo ci) {
        if (args.size() < 2 || !"abilities.flying".equals(args.get(0).trim())) return;

        boolean expectOne = "1".equals(args.get(1).trim());
        FlightMathMethodAccessor accessor = (FlightMathMethodAccessor) this;
        MathValue.ResultSupplier original = accessor.create$getSupplier();
        if (original == null) return;

        accessor.create$setSupplier(() -> {
            if (FlightAnimationSupport.isPackFlightAware() && FlightCompat.isCurrentEmfEntityJetpackFlying()) {
                // EMF encodes boolean animation values as +/-Infinity, not 1f/0f.
                return MathValue.fromBoolean(expectOne);
            }
            return original.get();
        });
    }
}
