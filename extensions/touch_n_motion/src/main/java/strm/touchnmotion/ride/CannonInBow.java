package strm.touchnmotion.ride;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/**
 * Supplementaries' boat with a cannon: the mod has the cannon in the stern, behind the one who rows -
 * right where they lean back with each stroke. So the cannon stands in the bow instead, the rower a
 * little aft of the middle with the paddles ({@link BoatSeats#riderAft}), and the cannon's blind
 * arc - the mod keeps it from turning on its own crew - is turned about with it.
 *
 * <p>Where the cannon is drawn, where its shot starts and the path shown while aiming are all one
 * method of the boat's, answered here. The shot is the server's: a client alone would draw a cannon
 * in the bow that fires from the stern, so it is moved only where the server has this mod too - in
 * one's own world, or on a server with it. No client class is named here; the server runs it.</p>
 *
 * <p>Optional, by name: nothing without Supplementaries, or with a version of it that has no such boat.</p>
 */
public final class CannonInBow {

    private static final String BOAT = "net.mehvahdjukaar.supplementaries.common.entities.CannonBoatEntity";
    /** Blocks ahead of the boat's middle that the cannon's own middle stands; and as far aft as its rower sits. */
    private static final double BOW = 9.0 / 16, RAFT_BOW = 9.0 / 16, AFT = 5.0 / 16, RAFT_AFT = 6.0 / 16;

    /** On a client: whether the server has this mod, and so fires from the bow. */
    public static BooleanSupplier client = () -> false;

    private static final ClassValue<Boolean> IS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) if (c.getName().equals(BOAT)) return true;
            return false;
        }
    };
    /** The boats whose cannon's blind arc has been turned about. */
    private static final Map<Entity, Boolean> TURNED = Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean failed;
    private static Method cannonOf, restraintOf, setRestraint, rotated, minYaw, clamp, orientationOf, setOrientation, snap;

    private CannonInBow() {
    }

    public static boolean is(Entity entity) {
        return entity != null && IS.get(entity.getClass());
    }

    /** Whether {@code boat} is a boat with a cannon and that cannon is in the bow. */
    public static boolean moved(Boat boat) {
        return is(boat) && !failed && (!boat.level().isClientSide() || client.getAsBoolean());
    }

    public static double aft(boolean raft) {
        return raft ? RAFT_AFT : AFT;
    }

    /** The cannon's place in the boat, as the mod has it ({@code own}: to the right, up, astern) or ours. */
    public static Vec3 offset(Boat boat, Vec3 own) {
        if (own == null || !moved(boat)) return own;
        return new Vec3(own.x, own.y, -(strm.touchnmotion.platform.ServerSide.isRaft(boat) ? RAFT_BOW : BOW));
    }

    /**
     * Once for a boat: the blind arc turned half about, and - on the server - a cannon that would now
     * be looking into it turned half about as well. A cannon never aimed looks astern, away from the
     * rower before it; in the bow that is at the rower, and it is put to look ahead. One aimed since
     * is never in the arc, so nothing is turned twice.
     */
    public static void tick(Boat boat) {
        if (!moved(boat) || TURNED.containsKey(boat)) return;
        TURNED.put(boat, Boolean.TRUE);
        try {
            if (cannonOf == null) cannonOf = boat.getClass().getMethod("getInternalCannon");
            Object cannon = cannonOf.invoke(boat);
            if (cannon == null) return;
            if (restraintOf == null) {
                Class<?> tile = cannon.getClass();
                restraintOf = tile.getMethod("getOrientationRestraints");
                Class<?> restraint = restraintOf.getReturnType();
                setRestraint = tile.getMethod("setRestraint", restraint);
                rotated = restraint.getMethod("rotated", Direction.class);
                minYaw = restraint.getMethod("minYawDeg");
                clamp = restraint.getMethod("clamp", Quaternionf.class);
                orientationOf = tile.getMethod("getLocalOrientation", float.class);
                setOrientation = tile.getMethod("setLocalOrientation", Quaternionf.class);
                snap = tile.getMethod("snapToWantedRotationInstantly");
            }
            Object own = restraintOf.invoke(cannon);
            float wanted = (Float) minYaw.invoke(own) + 180f;
            Object about = null;
            for (Direction way : Direction.values()) {
                Object turned = rotated.invoke(own, way);
                float off = Math.abs(net.minecraft.util.Mth.wrapDegrees((Float) minYaw.invoke(turned) - wanted));
                if (off < 1f) {
                    about = turned;
                    break;
                }
            }
            if (about == null) return;
            setRestraint.invoke(cannon, about);
            if (boat.level().isClientSide()) return;
            Quaternionf looks = new Quaternionf((Quaternionf) orientationOf.invoke(cannon, 1f));
            Quaternionf kept = (Quaternionf) clamp.invoke(about, new Quaternionf(looks));
            if (kept.equals(looks, 1e-3f)) return;
            setOrientation.invoke(cannon, new Quaternionf().rotationY((float) Math.PI).mul(looks));
            snap.invoke(cannon);
        } catch (Throwable t) {
            failed = true;
            org.slf4j.LoggerFactory.getLogger("TouchNMotion").warn("[TouchNMotion] could not move the cannon of a boat to its bow; left where the mod has it", t);
        }
    }
}
