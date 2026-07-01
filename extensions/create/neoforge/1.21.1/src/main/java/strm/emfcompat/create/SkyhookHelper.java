package strm.emfcompat.create;

import strm.emfcompat.create.mixin.PlayerSkyhookRendererAccessor;

import java.util.Set;
import java.util.UUID;

/**
 * Checks whether a player is currently skyhooking via Create's {@code PlayerSkyhookRenderer}.
 */
public final class SkyhookHelper {

    private SkyhookHelper() {
    }

    public static boolean isSkyhooking(UUID playerUuid) {
        Set<UUID> hangingPlayers = PlayerSkyhookRendererAccessor.emfcompatCreate$getHangingPlayers();
        return hangingPlayers != null && hangingPlayers.contains(playerUuid);
    }
}
