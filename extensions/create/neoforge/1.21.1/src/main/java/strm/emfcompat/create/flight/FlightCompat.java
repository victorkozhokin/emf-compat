package strm.emfcompat.create.flight;

import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import strm.emfcompat.create.EMFCompatCreateMod;
import traben.entity_model_features.models.animation.state.EMFState;

/**
 * Unified jetpack-flight dispatcher for the Create ecosystem. Both Create Cosmonautics
 * (rocketnautics) and Create Stuff 'N Additions expose a jetpack whose flight the resource
 * pack can animate via {@code nbt(abilities.flying, ...)}; this class decides whether the
 * currently animated player is flying any of them.
 *
 * <p>{@link CosmonauticsCompat} references a rocketnautics class, so it is only touched behind
 * the {@code ROCKETNAUTICS} gate — thanks to lazy class loading and {@code &&} short-circuiting
 * it never class-loads when the mod is absent. {@link CreateSaCompat} is data-driven and safe
 * to call unconditionally.</p>
 */
public final class FlightCompat {

    private static final boolean ROCKETNAUTICS = ModList.get().isLoaded("rocketnautics");

    private FlightCompat() {
    }

    /**
     * Resolves the entity currently being animated by EMF and checks it for active jetpack
     * flight in any supported Create-ecosystem mod. Never throws: EMF permanently disables a
     * model's animations if an exception escapes evaluation.
     */
    public static boolean isCurrentEmfEntityJetpackFlying() {
        try {
            if (!EMFCompatCreateMod.isEnabled()) {
                return false;
            }
            var state = EMFState.state();
            if (state == null || !(state.emfEntity() instanceof Player player)) {
                return false;
            }
            if (ROCKETNAUTICS && EMFCompatCreateMod.isCosmonautics() && CosmonauticsCompat.isJetpackFlying(player)) {
                return true;
            }
            return EMFCompatCreateMod.isCreateSa() && CreateSaCompat.isJetpackFlying(player);
        } catch (Throwable t) {
            return false;
        }
    }
}
