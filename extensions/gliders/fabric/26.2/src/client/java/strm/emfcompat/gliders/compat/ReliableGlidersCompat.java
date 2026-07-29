package strm.emfcompat.gliders.compat;

import net.minecraft.world.entity.player.Player;

/**
 * Compatibility with the Reliable Gliders mod (evandev). Only referenced behind
 * {@link GlidingState#isReliableGlidersLoaded()}, so the mod's classes are never loaded when it
 * is absent.
 *
 * <p>Reliable Gliders poses the arms directly on the model inside its own
 * {@code HumanoidModelMixin} — it uses no animation library, so nothing pauses EMF for it and the
 * pose would simply be animated over. Gliding state comes from the mod's public
 * {@code api.GlidingState.isGliding} tracker, which is synced for remote players.</p>
 */
public final class ReliableGlidersCompat {

    private ReliableGlidersCompat() {}

    public static boolean isGliding(Player player) {
        if (player == null) return false;
        try {
            return com.evandev.reliable_gliders.api.GlidingState.isGliding(player);
        } catch (Throwable t) {
            return false;
        }
    }
}
