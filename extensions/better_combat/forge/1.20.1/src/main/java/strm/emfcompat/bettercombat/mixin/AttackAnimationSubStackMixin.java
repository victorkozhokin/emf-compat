package strm.emfcompat.bettercombat.mixin;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.bettercombat.client.animation.AttackAnimationSubStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Function;

/**
 * Reduces Better Combat's global pitch-based body adjustment for attack animations.
 *
 * <p>Better Combat normally adds an {@link AdjustmentModifier} that rotates the whole model
 * (body, arms and legs) based on the player's camera pitch. The body rotation in particular
 * makes the character tilt as one rigid piece, which clashes with EMF animations. Arms and
 * legs are left with their original corrections so that Better Combat can still influence
 * them.</p>
 *
 * <p>This mimics the behaviour of FA Player Extension Compat, which scales the body pitch
 * contribution down to {@code 0.05} instead of {@code 0.75}.</p>
 */
@Mixin(value = AttackAnimationSubStack.class, remap = false)
public class AttackAnimationSubStackMixin {

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

    @ModifyVariable(
            method = "<init>",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static AdjustmentModifier emfcompat$reduceGlobalAttackAdjustment(AdjustmentModifier original) {
        Function<String, Optional<AdjustmentModifier.PartModifier>> source = getAdjustmentSource(original);
        if (source == null) {
            return original;
        }

        return new AdjustmentModifier(partName -> {
            Optional<AdjustmentModifier.PartModifier> result = source.apply(partName);
            if (result.isEmpty()
                    || !"body".equals(partName)
                    || FirstPersonMode.isFirstPersonPass()) {
                return result;
            }

            AdjustmentModifier.PartModifier modifier = result.get();
            Vec3f rotation = modifier.rotation();
            Vec3f reducedRotation = new Vec3f(
                    rotation.getX() * BODY_PITCH_FACTOR,
                    rotation.getY(),
                    rotation.getZ()
            );
            return Optional.of(new AdjustmentModifier.PartModifier(
                    reducedRotation,
                    modifier.offset()
            ));
        });
    }

    @SuppressWarnings("unchecked")
    private static Function<String, Optional<AdjustmentModifier.PartModifier>> getAdjustmentSource(AdjustmentModifier modifier) {
        if (ADJUSTMENT_SOURCE_FIELD == null) {
            return null;
        }
        try {
            return (Function<String, Optional<AdjustmentModifier.PartModifier>>) ADJUSTMENT_SOURCE_FIELD.get(modifier);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
