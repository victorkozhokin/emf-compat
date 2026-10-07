package strm.emfcompat.animationadditions.ride;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Where the second rider of a boat a player rows sits. The one who rows leans far back with each
 * stroke, into the place the game gives a passenger; so the passenger sits in the bow instead,
 * facing the rower. This is the rule for it, the same on a server and on a client - no client
 * class is named here, the server runs it too.
 *
 * <p>A server with the addon really seats the passenger there: where they are hit, where they see
 * from. On one without it only the clients with the addon draw them there, each for itself.</p>
 */
public final class BoatSeats {

    /** Blocks before the boat's middle: the passenger's seat in the bow. */
    public static final double BOW = 0.72;
    /** Blocks: the bow seat is this much higher than the rower's - on the bow's thwart, the rower's feet under it, and the hands clear of the rowlocks. */
    public static final double RAISED = 3.0 / 16;
    /** Degrees either way the passenger may turn from facing the rower: the game's own limit in a boat. */
    public static final float TURN = 105f;

    /** On a client: whether the passenger is drawn in the bow. Set by the client's set-up; a server has no say to ask. */
    public static BooleanSupplier client = () -> true;

    /** Blocks behind the boat's middle: where the one who rows a boat with a chest sits, the chest being in the bow. The paddles are as far aft. */
    private static final double AFT = 4.0 / 16, RAFT_AFT = 6.0 / 16;
    /** On a client: whether a boat's chest is drawn in the bow, and its rower so seated. */
    public static BooleanSupplier chestClient = () -> true;

    private BoatSeats() {
    }

    /**
     * Whether {@code rider} rows a boat whose passenger sits in the bow. The game moves a rower
     * forward to make room behind for a passenger; with the passenger in the bow the rower keeps
     * the seat they have alone - the paddles' handles are where a rower alone can reach them.
     */
    public static boolean rowsWithBow(Boat boat, Entity rider) {
        List<Entity> riders = boat.getPassengers();
        return riders.size() >= 2 && riders.get(0) == rider && inBow(boat, riders.get(1));
    }

    /** Whether {@code passenger} is the second rider of a {@code boat} a player rows, and so sits in the bow: another player, or a mob taken along. */
    public static boolean inBow(Boat boat, Entity passenger) {
        List<Entity> riders = boat.getPassengers();
        if (riders.size() < 2 || riders.get(1) != passenger) return false;
        if (!(riders.get(0) instanceof Player)) return false;
        return !boat.level().isClientSide || client.getAsBoolean();
    }

    /** Blocks aft of the middle that the rider of {@code boat}, a boat with its chest in the bow, sits: in a boat as far as leaves the back clear of the stern's board, on a raft - it has none - further. */
    public static double aft(Boat boat) {
        return boat.getVariant().isRaft() ? RAFT_AFT : AFT;
    }

    /** Whether {@code boat} has a chest and that chest is in the bow: its rider then sits aft ({@link #aft}). */
    public static boolean chestInBow(Boat boat) {
        return boat instanceof ChestBoat && (!boat.level().isClientSide || chestClient.getAsBoolean());
    }
}
