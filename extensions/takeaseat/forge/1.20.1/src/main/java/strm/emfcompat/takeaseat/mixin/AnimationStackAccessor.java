package strm.emfcompat.takeaseat.mixin;

import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.ArrayList;

/** Exposes the layer list, which this version of Player Animator keeps private. */
@Mixin(AnimationStack.class)
public interface AnimationStackAccessor {

    @Accessor("layers")
    ArrayList<Pair<Integer, IAnimation>> emfcompat$getLayers();
}
