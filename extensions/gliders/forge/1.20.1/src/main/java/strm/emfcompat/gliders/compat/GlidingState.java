package strm.emfcompat.gliders.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;

/**
 * Central dispatcher for gliding detection across the supported glider mods.
 * Every supported mod is a soft dependency: its compat classes are only touched
 * when the mod is actually loaded, so any subset of them may be installed.
 */
public final class GlidingState {

    public static final String SOURCE = "gliders";

    private static final boolean PARAGLIDERS_LOADED = ModList.get().isLoaded("paraglider");
    private static final boolean VC_GLIDERS_LOADED = ModList.get().isLoaded("vc_gliders");

    private GlidingState() {}

    public static boolean isParaglidersLoaded() {
        return PARAGLIDERS_LOADED;
    }

    public static boolean isVcGlidersLoaded() {
        return VC_GLIDERS_LOADED;
    }

    public static boolean anyGliderModLoaded() {
        return PARAGLIDERS_LOADED || VC_GLIDERS_LOADED;
    }

    /**
     * Whether the player is paragliding (Tictim's Paragliders). False when the
     * mod is not installed.
     */
    public static boolean isParagliding(Player player) {
        return PARAGLIDERS_LOADED && ParagliderCompat.isParagliding(player);
    }

    /**
     * Whether the player is gliding with a VC glider. False when the mod is not
     * installed.
     */
    public static boolean isVcGliding(Player player) {
        return VC_GLIDERS_LOADED && VCGlidersCompat.isGliding(player);
    }

    /**
     * Whether the player is gliding with any supported glider mod.
     */
    public static boolean isGliding(Player player) {
        return isParagliding(player) || isVcGliding(player);
    }

    /**
     * Resolves the entity currently being animated by EMF and checks it for
     * gliding with any supported mod. Never throws: EMF disables ALL animations
     * of a model if an exception escapes animation evaluation.
     */
    public static boolean isCurrentEmfEntityGliding() {
        try {
            var state = EMFAnimationEntityContext.getEmfState();
            return state != null && state.emfEntity() instanceof Player player && isGliding(player);
        } catch (Throwable t) {
            return false;
        }
    }
}
