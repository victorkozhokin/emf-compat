package strm.emfcompat.takeaseat.mixin;

import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes which animation a layer is playing, so a Sitting+ one can be told apart. */
@Mixin(KeyframeAnimationPlayer.class)
public interface KeyframeAnimationPlayerAccessor {

    @Accessor("data")
    KeyframeAnimation emfcompat$getData();
}
