package strm.emfcompat.create;

import com.addon.gancho.AddonGanchoNet;
import com.addon.gancho.client.HandHoldHandler;
import net.minecraft.world.entity.player.Player;
import strm.emfcompat.create.mixin.PlayerGrappleHookAccessor;

import java.util.Map;
import java.util.UUID;

/**
 * Checks grapple/cable-trolley state from Create: Grappling Hooks ({@code addon_gancho}).
 * This class is only invoked when {@code addon_gancho} is loaded.
 */
public final class GrappleHookHelper {

    private GrappleHookHelper() {
    }

    public static boolean isGrappling(Player player) {
        UUID playerUuid = player.getUUID();
        Map<UUID, UUID> handLinks = PlayerGrappleHookAccessor.emfcompatCreate$getHandLinks();
        boolean holdingSomeone = handLinks != null && handLinks.containsKey(playerUuid);
        boolean beingHeld = AddonGanchoNet.holderOf(playerUuid) != null;
        boolean ridingCableTrolley = HandHoldHandler.isHangingRider(player, player.level());
        return holdingSomeone || beingHeld || ridingCableTrolley;
    }
}
