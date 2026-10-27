package strm.emfcompat.bettercombat.mixin;

import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import net.bettercombat.client.animation.AnimationRegistry;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import strm.emfcompat.bettercombat.EMFCompatBetterCombatMod;

import java.util.HashMap;
import java.util.Map;

/**
 * The "Softer slam" option on Forge 1.20.1: Better Combat looks its attack animations up by name in
 * {@link AnimationRegistry#animations}; once they are loaded the map is wrapped so that, with the
 * option on, {@code bettercombat:one_handed_slam} answers with the softened copy shipped here
 * ({@code one_handed_slam_fixed}). Read at every lookup, so the option takes effect at once.
 */
@Mixin(value = AnimationRegistry.class, remap = false)
public class AnimationRegistryMixin {

    @Inject(method = "load", at = @At("TAIL"))
    private static void emfcompat$softSlam(ResourceManager resources, CallbackInfo ci) {
        if (!(AnimationRegistry.animations instanceof SoftSlam)) AnimationRegistry.animations = new SoftSlam(AnimationRegistry.animations);
    }

    /** The animations by name, the slam answered with its softened copy while the option is on. */
    private static final class SoftSlam extends HashMap<String, KeyframeAnimation> {
        private static final String SLAM = "bettercombat:one_handed_slam", SOFT = "bettercombat:one_handed_slam_fixed";

        SoftSlam(Map<String, KeyframeAnimation> loaded) {
            super(loaded);
        }

        @Override
        public KeyframeAnimation get(Object name) {
            if (SLAM.equals(name) && EMFCompatBetterCombatMod.isSoftSlam()) {
                KeyframeAnimation soft = super.get(SOFT);
                if (soft != null) return soft;
            }
            return super.get(name);
        }
    }
}
