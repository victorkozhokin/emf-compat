package strm.emfcompat.gliders.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.gliders.compat.FlightAnimationSupport;
import strm.emfcompat.gliders.compat.GlidingState;
import traben.entity_model_features.models.animation.AnimSetupContext;
import traben.entity_model_features.models.animation.math.expression_tree.MathMethod;
import traben.entity_model_features.models.animation.math.expression_tree.MathValue;
import traben.entity_model_features.models.animation.math.methods.emf.NBTMethod;

import java.util.List;

/**
 * Lambda-evaluation counterpart of {@link NBTMethodMixin}: if an animation containing
 * {@code nbt(abilities.flying, ...)} falls back to supplier-based evaluation instead of the
 * ASM-compiled path, wrap the supplier with the same gliding spoof. The spoof itself
 * is checked at evaluation time, because the supplier is created during animation parsing,
 * before flight-animation detection has run.
 */
@Mixin(NBTMethod.class)
public class NBTMethodLambdaMixin {

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void gliders$wrapFlyingSupplier(List<String> args, boolean isInverted, AnimSetupContext context, CallbackInfo ci) {
        if (args.size() < 2 || !"abilities.flying".equals(args.get(0).trim())) return;

        boolean expectOne = "1".equals(args.get(1).trim());
        MathMethodAccessor accessor = (MathMethodAccessor) this;
        MathValue.ResultSupplier original = accessor.gliders$getSupplier();
        if (original == null) return;

        accessor.gliders$setSupplier(() -> {
            if (FlightAnimationSupport.isPackFlightAware()
                    && GlidingState.isCurrentEmfEntityGliding()) {
                // EMF encodes boolean animation values as +/-Infinity, not 1f/0f.
                return MathValue.fromBoolean(expectOne);
            }
            return original.get();
        });
    }
}
