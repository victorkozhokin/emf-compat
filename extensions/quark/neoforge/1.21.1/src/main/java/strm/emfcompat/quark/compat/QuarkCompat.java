package strm.emfcompat.quark.compat;

import net.minecraft.world.entity.player.Player;
import org.violetmoon.quark.content.tweaks.client.emote.EmoteBase;
import org.violetmoon.quark.content.tweaks.client.emote.EmoteHandler;

/**
 * Helpers for detecting Quark emote state on a player.
 */
public final class QuarkCompat {

    private QuarkCompat() {
    }

    /**
     * Returns {@code true} if the player is currently playing a Quark emote.
     */
    public static boolean isPlayingEmote(Player player) {
        return EmoteHandler.getPlayerEmote(player) != null;
    }

    /**
     * Returns the active Quark emote for the player, or {@code null} if none is playing.
     */
    public static EmoteBase getActiveEmote(Player player) {
        return EmoteHandler.getPlayerEmote(player);
    }
}
