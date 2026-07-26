package strm.emfcompat.create.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import traben.entity_model_features.models.animation.math.expression_tree.MathMethod;
import traben.entity_model_features.models.animation.math.expression_tree.MathValue;

/**
 * Accessor for the evaluation supplier stored in {@link MathMethod}. The accessor methods use
 * a {@code create$} prefix so they never collide with an identically-targeted accessor from
 * another addon on the same EMF {@code MathMethod} class.
 */
@Mixin(MathMethod.class)
public interface FlightMathMethodAccessor {

    @Accessor(value = "supplier", remap = false)
    MathValue.ResultSupplier create$getSupplier();

    @Accessor(value = "supplier", remap = false)
    void create$setSupplier(MathValue.ResultSupplier supplier);
}
