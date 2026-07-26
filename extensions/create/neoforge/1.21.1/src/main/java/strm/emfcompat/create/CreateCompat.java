package strm.emfcompat.create;

import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/**
 * Create integration helpers.
 *
 * <p>Skyhook and Grappling Hooks are no longer handled with EMF pause / force-vanilla-model
 * conditions (which also disabled player-expression animations). Both mods pose the whole body
 * during model setup, so their poses are captured and restored over EMF by
 * {@link strm.emfcompat.create.mixin.PlayerSkyhookRendererMixin} and
 * {@link strm.emfcompat.create.mixin.PlayerModelGrappleMixin} respectively.</p>
 */
public final class CreateCompat {

    private CreateCompat() {
    }

    /**
     * Returns true when the player is performing a Create activity (skyhook or grapple) that
     * should suppress NEA's item-swap animation. Gated on the master switch and the NEA
     * item-swap toggle.
     */
    public static boolean shouldDisableItemSwap(Player player) {
        if (!EMFCompatCreateMod.isEnabled() || !EMFCompatCreateMod.isNeaItemSwap()) return false;
        if (SkyhookHelper.isSkyhooking(player.getUUID())) return true;
        if (ModList.get().isLoaded("addon_gancho") && GrappleHookHelper.isGrappling(player)) return true;
        return false;
    }
}
