package strm.emfcompat.carryon.compat;

import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/**
 * Optional Corpse integration guard. Carry On must not reference Corpse classes
 * directly because Corpse is not a required dependency.
 */
public final class CorpseCompatGuard {

    private static final boolean CORPSE_LOADED = ModList.get().isLoaded("corpse");
    private static final String DUMMY_CLASS_PREFIX = "de.maxhenkel.corpse.entities.Dummy";

    private CorpseCompatGuard() {
    }

    /**
     * Returns {@code true} when the player is a Corpse mod render dummy
     * ({@code DummyPlayer} / {@code DummySkeleton}). These entities reuse the
     * deceased player's UUID but must not participate in Carry On pose tracking.
     */
    public static boolean isCorpseDummyPlayer(Player player) {
        if (!CORPSE_LOADED || player == null) {
            return false;
        }
        return player.getClass().getName().startsWith(DUMMY_CLASS_PREFIX);
    }
}
