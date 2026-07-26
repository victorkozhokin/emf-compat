package strm.emfcompat.gliders.compat;

import net.minecraft.world.entity.player.Player;
import net.venturecraft.gliders.util.GliderUtil;

/**
 * Compatibility with the Gliders mod (vc_gliders, Jeryn99). Only referenced
 * behind {@link GlidingState#isVcGlidersLoaded()}, so this class is never
 * loaded when the mod is absent.
 */
public final class VCGlidersCompat {

    private VCGlidersCompat() {}

    /**
     * Whether the player is currently gliding with an active VC glider. Uses the
     * mod's own ground-truth check; the equipped glider stack (and its glide
     * component) is synced for remote players. Never throws.
     */
    public static boolean isGliding(Player player) {
        if (player == null) return false;
        try {
            return GliderUtil.isGlidingWithActiveGlider(player);
        } catch (Throwable t) {
            return false;
        }
    }
}
