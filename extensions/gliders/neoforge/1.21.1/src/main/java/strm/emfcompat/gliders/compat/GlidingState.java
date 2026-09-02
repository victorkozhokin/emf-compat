package strm.emfcompat.gliders.compat;

import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import strm.emfcompat.gliders.GlidersEMFCompat;
import traben.entity_model_features.models.animation.state.EMFState;

/**
 * Central dispatcher for gliding detection across the supported glider mods.
 * Every supported mod is a soft dependency: its compat classes are only touched
 * when the mod is actually loaded, so any subset of them may be installed.
 */
public final class GlidingState {

    public static final String SOURCE = "gliders";

    private static final boolean PARAGLIDERS_LOADED = ModList.get().isLoaded("paraglider");
    private static final boolean VC_GLIDERS_LOADED = ModList.get().isLoaded("vc_gliders");
    private static final boolean RELIABLE_GLIDERS_LOADED = ModList.get().isLoaded("reliable_gliders");

    private GlidingState() {}

    public static boolean isParaglidersLoaded() {
        return PARAGLIDERS_LOADED;
    }

    public static boolean isVcGlidersLoaded() {
        return VC_GLIDERS_LOADED;
    }

    public static boolean isReliableGlidersLoaded() {
        return RELIABLE_GLIDERS_LOADED;
    }

    public static boolean anyGliderModLoaded() {
        return PARAGLIDERS_LOADED || VC_GLIDERS_LOADED || RELIABLE_GLIDERS_LOADED;
    }

    /**
     * Whether the player is paragliding (Tictim's Paragliders). False when the
     * mod is not installed.
     */
    public static boolean isParagliding(Player player) {
        return PARAGLIDERS_LOADED && GlidersEMFCompat.isEnabled() && GlidersEMFCompat.isParagliderEnabled()
                && ParagliderCompat.isParagliding(player);
    }

    /**
     * Whether the player is gliding with a VC glider. False when the mod is not
     * installed.
     */
    public static boolean isVcGliding(Player player) {
        return VC_GLIDERS_LOADED && GlidersEMFCompat.isEnabled() && GlidersEMFCompat.isVcGlidersEnabled()
                && VCGlidersCompat.isGliding(player);
    }

    /**
     * Whether the player is gliding with a Reliable Glider. False when the mod is
     * not installed.
     */
    public static boolean isReliableGliding(Player player) {
        return RELIABLE_GLIDERS_LOADED && GlidersEMFCompat.isEnabled() && GlidersEMFCompat.isReliableGlidersEnabled()
                && ReliableGlidersCompat.isGliding(player);
    }

    /**
     * Whether the player is gliding with any supported glider mod.
     */
    public static boolean isGliding(Player player) {
        return isParagliding(player) || isVcGliding(player) || isReliableGliding(player);
    }

    /**
     * Whether the glider is still opening — the swinging deploy animation is playing and the
     * player should not be treated as in flight yet.
     *
     * <p>Only VC Gliders has a deploy animation: it plays a full-body Player Animator clip that
     * starts with the glider snapping open. Paragliders and Reliable Gliders just pose the arms
     * in {@code setupAnim} with nothing to wait for, so they are never "deploying".</p>
     */
    public static boolean isDeploying(Player player) {
        return VC_GLIDERS_LOADED && GlidersEMFCompat.isEnabled() && GlidersEMFCompat.isVcGlidersEnabled()
                && VCGlidersCompat.isDeploying(player);
    }

    /**
     * Whether the player should be shown in the flight pose — gliding, and past any deploy
     * animation. This is what drives the {@code abilities.flying} spoof, so the pack's flight
     * animation only takes over once the glider has finished opening.
     */
    public static boolean isInFlightPose(Player player) {
        return isGliding(player) && !isDeploying(player);
    }

    /**
     * Resolves the entity currently being animated by EMF and checks it for
     * gliding with any supported mod. Never throws: EMF disables ALL animations
     * of a model if an exception escapes animation evaluation.
     */
    public static boolean isCurrentEmfEntityGliding() {
        try {
            var state = EMFState.state();
            return state != null && state.emfEntity() instanceof Player player && isGliding(player);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * {@link #isInFlightPose(Player)} for the entity currently being animated by EMF. Never
     * throws, for the same reason as {@link #isCurrentEmfEntityGliding()}.
     */
    public static boolean isCurrentEmfEntityInFlightPose() {
        try {
            var state = EMFState.state();
            return state != null && state.emfEntity() instanceof Player player && isInFlightPose(player);
        } catch (Throwable t) {
            return false;
        }
    }
}
