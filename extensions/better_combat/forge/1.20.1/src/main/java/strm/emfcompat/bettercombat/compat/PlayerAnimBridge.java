package strm.emfcompat.bettercombat.compat;

import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * Thin bridge to Player Animator (kosmx). Better Combat already requires Player Animator, so these
 * classes are always present here. Used by the generic capture to detect any active player
 * animation (e.g. a Spell Engine / RPG Series cast).
 */
public final class PlayerAnimBridge {

    private PlayerAnimBridge() {
    }

    /** @return {@code true} if any Player Animator animation is currently active on the player. */
    public static boolean hasActiveAnimation(AbstractClientPlayer player) {
        try {
            AnimationStack stack = PlayerAnimationAccess.getPlayerAnimLayer(player);
            return stack != null && stack.isActive();
        } catch (Throwable t) {
            return false;
        }
    }
}
