package strm.emfcompat.core.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import strm.emfcompat.core.SubLevelHeight;
import traben.entity_model_features.models.animation.math.EMFMath;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.animation.state.EMFState;

/**
 * {@code pos_y} without a Sable sub-level's own rise and fall (see {@link SubLevelHeight}).
 *
 * <p>{@code remap = false}: the target is EMF's own class.</p>
 */
@Mixin(value = EMFMath.class, remap = false)
public abstract class EMFMathMixin {

    @Shadow
    private static EMFEntityRenderState emfState() {
        throw new AssertionError();
    }

    @Inject(method = "getEntityY", at = @At("RETURN"), cancellable = true, remap = false)
    private static void emfcompat$steadyOnSubLevel(CallbackInfoReturnable<Float> cir) {
        try {
            EMFEntityRenderState state = emfState();
            if (state == null || !(state.emfEntity() instanceof Entity entity)) return;
            float y = cir.getReturnValueF();
            float adjusted = SubLevelHeight.adjust(entity, y, EMFMath.getTickDelta(), EMFState.getFrameCounter());
            if (adjusted != y) cir.setReturnValue(adjusted);
        } catch (Throwable ignored) {
            // A pack variable must never take EMF's animation down with it.
        }
    }
}
