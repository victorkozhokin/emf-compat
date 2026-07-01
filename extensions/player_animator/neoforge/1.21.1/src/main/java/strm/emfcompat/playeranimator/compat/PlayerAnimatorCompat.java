package strm.emfcompat.playeranimator.compat;

import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * Helpers for detecting Player Animator state on a client player.
 */
public final class PlayerAnimatorCompat {

    private PlayerAnimatorCompat() {
    }

    /**
     * Returns {@code true} if the player currently has an active Player Animator animation.
     */
    public static boolean isAnimationActive(AbstractClientPlayer player) {
        if (!(player instanceof IAnimatedPlayer animated)) {
            return false;
        }
        var animation = animated.playerAnimator_getAnimation();
        return animation != null && animation.isActive();
    }
}
