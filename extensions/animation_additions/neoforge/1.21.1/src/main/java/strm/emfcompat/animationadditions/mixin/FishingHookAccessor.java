package strm.emfcompat.animationadditions.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether something is on the hook: sent by the server to every client, kept by the float in a field of its own. */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {

    @Accessor("biting")
    boolean emfcompat$biting();
}
