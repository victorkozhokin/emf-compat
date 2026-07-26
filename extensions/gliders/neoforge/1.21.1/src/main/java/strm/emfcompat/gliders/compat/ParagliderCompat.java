package strm.emfcompat.gliders.compat;

import net.minecraft.world.entity.player.Player;
import tictim.paraglider.api.movement.Movement;

/**
 * Compatibility with Tictim's Paragliders. Only referenced behind
 * {@link GlidingState#isParaglidersLoaded()}, so this class is never loaded
 * when the mod is absent.
 */
public final class ParagliderCompat {

    private ParagliderCompat() {}

    /**
     * Whether the player is currently gliding with a paraglider. Uses the mod's
     * public movement API, which is also synced for remote players. Defensive
     * against the movement attachment being unavailable early in the game loop.
     */
    public static boolean isParagliding(Player player) {
        if (player == null) return false;
        try {
            Movement movement = Movement.get(player);
            return movement != null && movement.state() != null && movement.state().paragliding();
        } catch (Throwable t) {
            return false;
        }
    }
}
