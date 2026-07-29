package strm.emfcompat.gliders.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;
import strm.emfcompat.gliders.GlidersEMFCompat;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;

/**
 * Gliding detection. Reliable Gliders is a soft dependency: its classes are only touched when the
 * mod is actually loaded, so the addon stays inert without it.
 */
public final class GlidingState {

    public static final String SOURCE = "gliders";

    private static final boolean RELIABLE_GLIDERS_LOADED =
            FabricLoader.getInstance().isModLoaded("reliable_gliders");

    private GlidingState() {}

    public static boolean isReliableGlidersLoaded() {
        return RELIABLE_GLIDERS_LOADED;
    }

    /** Whether the player is gliding with a Reliable Glider. */
    public static boolean isGliding(Player player) {
        return RELIABLE_GLIDERS_LOADED
                && GlidersEMFCompat.isEnabled()
                && GlidersEMFCompat.isReliableGlidersEnabled()
                && ReliableGlidersCompat.isGliding(player);
    }

    /**
     * Whether the entity EMF is currently animating is gliding. Named "in flight pose" to match
     * the NeoForge/Forge builds, where it also excludes the glider's deploy phase — Reliable
     * Gliders has no deploy animation, so here the two are the same thing.
     *
     * <p>Never throws: EMF disables ALL animations of a model if an exception escapes animation
     * evaluation.</p>
     */
    public static boolean isCurrentEmfEntityInFlightPose() {
        try {
            var state = EMFAnimationEntityContext.getEmfState();
            return state != null && state.emfEntity() instanceof Player player && isGliding(player);
        } catch (Throwable t) {
            return false;
        }
    }
}
