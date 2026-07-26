package strm.emfcompat.gliders.compat;

import net.minecraft.world.entity.player.Player;

/**
 * Compatibility with the Reliable Gliders mod (evandev). Only referenced behind
 * {@link GlidingState#isReliableGlidersLoaded()}, so the mod's classes are never loaded when it
 * is absent.
 *
 * <p>Like Tictim's Paragliders, Reliable Gliders poses the arms inside {@code setupAnim} (its own
 * {@code HumanoidModelMixin} raises both arms while gliding), so its arm pose is captured by the
 * addon's {@code HumanoidModelMixin}. Gliding state comes from the mod's public
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
