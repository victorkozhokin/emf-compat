package strm.emfcompat.bettercombat.mixin;

import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Redirects Better Combat's one-handed slam animations to scaled-down versions.
 *
 * <p>The original {@code bettercombat:one_handed_slam} and
 * {@code bettercombat:one_handed_slam_instant} animations tilt and displace the torso
 * very aggressively. This mixin returns fixed variants (with torso transforms scaled by
 * {@code 0.35}) whenever anything requests the original animations through
 * {@link PlayerAnimationRegistry#getAnimation(ResourceLocation)}.</p>
 */
@Mixin(value = PlayerAnimationRegistry.class, remap = false)
public class BetterCombatOneHandedSlamMixin {
    private static final ResourceLocation ORIGINAL_SLAM = new ResourceLocation("bettercombat", "one_handed_slam");
    private static final ResourceLocation INSTANT_SLAM = new ResourceLocation("bettercombat", "one_handed_slam_instant");
    private static final ResourceLocation FIXED_SLAM = new ResourceLocation("bettercombat", "one_handed_slam_fixed");
    private static final ResourceLocation FIXED_INSTANT_SLAM = new ResourceLocation("bettercombat", "one_handed_slam_instant_fixed");

    @ModifyVariable(
            method = "getAnimation(Lnet/minecraft/resources/ResourceLocation;)Ldev/kosmx/playerAnim/core/data/KeyframeAnimation;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static ResourceLocation emfcompat$redirectOneHandedSlam(ResourceLocation id) {
        if (ORIGINAL_SLAM.equals(id)) {
            return FIXED_SLAM;
        }
        if (INSTANT_SLAM.equals(id)) {
            return FIXED_INSTANT_SLAM;
        }
        return id;
    }
}
