package strm.emfcompat.animationadditions.mixin;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The walk's phase, which the pack's legs (limb_swing) are drawn from. */
@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {

    @Accessor("position")
    float emfcompat$position();

    @Accessor("position")
    void emfcompat$setPosition(float position);
}
