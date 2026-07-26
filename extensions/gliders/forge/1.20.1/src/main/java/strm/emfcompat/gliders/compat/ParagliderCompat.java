package strm.emfcompat.gliders.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import tictim.paraglider.api.item.Paraglider;

/**
 * Compatibility with Tictim's Paragliders. Only referenced behind
 * {@link GlidingState#isParaglidersLoaded()}, so this class is never loaded
 * when the mod is absent.
 */
public final class ParagliderCompat {

    private ParagliderCompat() {}

    /**
     * Whether the player is currently gliding. On Forge 1.20.1 the mod applies
     * the pose from its own PlayerModel mixin based on the main-hand paraglider
     * item, so the same check is used here as the ground truth.
     */
    public static boolean isParagliding(Player player) {
        if (player == null) return false;
        try {
            ItemStack stack = player.getMainHandItem();
            return stack.getItem() instanceof Paraglider paraglider && paraglider.isParagliding(stack);
        } catch (Throwable t) {
            return false;
        }
    }
}
