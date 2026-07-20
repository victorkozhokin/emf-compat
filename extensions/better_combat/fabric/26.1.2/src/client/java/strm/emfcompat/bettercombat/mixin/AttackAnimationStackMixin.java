package strm.emfcompat.bettercombat.mixin;

import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.modifier.AdjustmentModifier;
import com.zigythebird.playeranimcore.math.Vec3f;
import net.bettercombat.client.animation.AttackAnimationStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Reduces Better Combat's global pitch-based body adjustment for attack animations.
 *
 * <p>Better Combat creates an {@link AdjustmentModifier} that rotates the whole model
 * (body, arms and legs) based on the player's camera pitch. The body rotation in particular
 * makes the character tilt as one rigid piece, which clashes with EMF animations. Arms and
 * legs are left with their original corrections so that Better Combat can still influence
 * them.</p>
 *
 * <p>This mimics the behaviour of FA Player Extension Compat, which scales the body pitch
 * contribution down to {@code 0.05} instead of {@code 0.75}.</p>
 */
@Mixin(value = AttackAnimationStack.class, priority = 1200)
public class AttackAnimationStackMixin {

    private static final float BODY_PITCH_FACTOR = 0.05F / 0.75F;

    private static final Field ADJUSTMENT_SOURCE_FIELD;

    static {
        Field field;
        try {
            field = AdjustmentModifier.class.getDeclaredField("source");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            field = null;
        }
        ADJUSTMENT_SOURCE_FIELD = field;
    }

    @Inject(
            method = "createAttackAdjustment",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void emfcompat$reduceGlobalAttackAdjustment(CallbackInfoReturnable<AdjustmentModifier> cir) {
        AdjustmentModifier original = cir.getReturnValue();
        BiFunction<String, AnimationData, Optional<AdjustmentModifier.PartModifier>> source = getAdjustmentSource(original);
        if (source == null) {
            return;
        }

        cir.setReturnValue(new AdjustmentModifier((partName, data) -> {
            Optional<AdjustmentModifier.PartModifier> result = source.apply(partName, data);
            if (result.isEmpty()
                    || !"body".equals(partName)
                    || data.isFirstPersonPass()) {
                return result;
            }

            AdjustmentModifier.PartModifier modifier = result.get();
            Vec3f rotation = modifier.rotation();
            Vec3f reducedRotation = new Vec3f(
                    rotation.x() * BODY_PITCH_FACTOR,
                    rotation.y(),
                    rotation.z()
            );
            return Optional.of(new AdjustmentModifier.PartModifier(
                    reducedRotation,
                    modifier.scale(),
                    modifier.offset()
            ));
        }));
    }

    @SuppressWarnings("unchecked")
    private static BiFunction<String, AnimationData, Optional<AdjustmentModifier.PartModifier>> getAdjustmentSource(AdjustmentModifier modifier) {
        if (ADJUSTMENT_SOURCE_FIELD == null) {
            return null;
        }
        try {
            return (BiFunction<String, AnimationData, Optional<AdjustmentModifier.PartModifier>>) ADJUSTMENT_SOURCE_FIELD.get(modifier);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
