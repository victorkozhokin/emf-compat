package strm.emfcompat.parcool.compat;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.parcool.EMFCompatParCoolMod;

import java.util.UUID;

/**
 * A hang belongs to ParCool alone: while one runs, no addon poses the player.
 *
 * <p>ParCool's hangs — under a bar, off a ledge, and the climb up off one — are drawn by the
 * resource pack from the {@code parcool_*} variables, and the core restores addon poses
 * <em>after</em> the pack has animated. So an addon holding the arms overwrites the pack's hands,
 * and since a hang reads almost entirely as "both arms up", the player is left standing upright in
 * mid-air. Carry On, TACZ, Better Combat, Hackers 'n Slashers and Exposure all did this.</p>
 *
 * <p>Sharing the hands was tried and dropped: splitting them (one hand holding on, the other left
 * to the addon) is not worth what it costs to read. A hang is two-handed, so ParCool simply claims
 * the player through {@link PoseManager#setExclusive} and the addons' poses are dropped for its
 * duration. Nothing here has to reproduce the hang — the claim only keeps everyone else off it.</p>
 *
 * <p>TACZ is the one addon that may get better than silence: if its gun can be put away into the
 * holster for the length of the move, it should be, rather than simply frozen. Until then it is
 * treated like the rest.</p>
 *
 * <p>Only hangs. A crouch, a run or a charge leaves the arms shared as before — those were checked
 * in game and read correctly.</p>
 */
public final class ParCoolHangHold {

    /** The claim's owner while a pack draws the hang. It saves no poses of its own. */
    public static final String SOURCE = "parcool_hang";

    /** Climbing up off a ledge is the same two-handed move continuing, so it is claimed too. */
    private static final String CLIMB_UP = "parcool:climb_up";
    private static final String CLIMB_UP_JUMP = "parcool:climb_up_jump";

    private ParCoolHangHold() {
    }

    /**
     * Claims the player for the length of a hang, or releases the claim. Called once per player
     * render, after ParCool has posed the model.
     */
    public static void capture(AbstractClientPlayer player) {
        UUID uuid = player.getUUID();
        if (EMFCompatParCoolMod.isEnabled() && hanging(player)) {
            // The claim is the pack's only where a pack draws this hang. With none that does - the
            // module is not turned on, or the pack is another one - it is ParCool's own pose that is
            // kept: claimed for nobody, the hang was drawn by nobody and the arms hung at the sides.
            PoseManager.setExclusive(uuid, packDraws(player) ? SOURCE : ParCoolPose.SOURCE);
        } else {
            PoseManager.clearExclusive(uuid, SOURCE);
            PoseManager.clearExclusive(uuid, ParCoolPose.SOURCE);
        }
    }

    /** Whether a pack is animating the hang this player is in, by having read its variables. */
    private static boolean packDraws(AbstractClientPlayer player) {
        if (ParCoolPackVariables.underBar(player)) return ParCoolPackVariables.packAnimates(player, "bar");
        if (ParCoolPackVariables.onLedge(player)) return ParCoolPackVariables.packAnimates(player, "hang");
        return ParCoolPackVariables.packAnimates(player, "climb");
    }

    /**
     * Whether whatever is in this player's hands should be left undrawn: both hands are on a bar or
     * a ledge, so nothing can be in them. Asked by the held-item layer, for any player.
     */
    public static boolean hidesHeldItems(Player player) {
        return EMFCompatParCoolMod.isEnabled()
                && player instanceof AbstractClientPlayer client
                && hanging(client);
    }

    private static boolean hanging(AbstractClientPlayer player) {
        return ParCoolPackVariables.underBar(player)
                || ParCoolPackVariables.onLedge(player)
                || ParCoolPackVariables.isRunning(player, CLIMB_UP)
                || ParCoolPackVariables.isRunning(player, CLIMB_UP_JUMP);
    }
}
