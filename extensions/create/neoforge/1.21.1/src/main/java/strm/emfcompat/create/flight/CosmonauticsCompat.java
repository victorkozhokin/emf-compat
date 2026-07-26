package strm.emfcompat.create.flight;

import dev.devce.rocketnautics.content.items.JetpackItem;
import net.minecraft.world.entity.player.Player;

/**
 * Detects whether a player is currently flying with a Create Cosmonautics (rocketnautics)
 * jetpack. Referenced only behind a {@code ModList.isLoaded("rocketnautics")} gate in
 * {@link FlightCompat}, so the {@link JetpackItem} class is never loaded when the mod is absent.
 */
public final class CosmonauticsCompat {

    private CosmonauticsCompat() {
    }

    /**
     * Returns {@code true} if the player's jetpack is toggled on and the player is airborne.
     * The worn item stack is synced for remote players, so this is safe for any client player.
     */
    public static boolean isJetpackFlying(Player player) {
        return JetpackItem.isActive(player) && !player.onGround();
    }
}
