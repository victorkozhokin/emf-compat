package strm.emfcompat.gliders.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import traben.entity_model_features.models.animation.math.expression_tree.MathMethod;
import traben.entity_model_features.models.animation.math.expression_tree.MathValue;

/**
 * Accessor for the evaluation supplier stored in {@link MathMethod}.
 */
@Mixin(MathMethod.class)
public interface MathMethodAccessor {

    @Accessor(value = "supplier", remap = false)
    MathValue.ResultSupplier gliders$getSupplier();

    @Accessor(value = "supplier", remap = false)
    void gliders$setSupplier(MathValue.ResultSupplier supplier);
}
