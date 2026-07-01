package strm.emfcompat.create.mixin;

import com.addon.gancho.AddonGanchoNet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

@Mixin(value = AddonGanchoNet.class, remap = false)
public interface PlayerGrappleHookAccessor {

    @Accessor("HAND_LINKS")
    static Map<UUID, UUID> emfcompatCreate$getHandLinks() {
        throw new AssertionError();
    }
}
