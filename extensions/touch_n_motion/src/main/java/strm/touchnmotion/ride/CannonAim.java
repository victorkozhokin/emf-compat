package strm.touchnmotion.ride;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Aiming the cannon of Supplementaries' boat: where the hands go - onto the cannon's breech, one to
 * a side, going round with it as it is turned ({@link BoatRide} takes them off the paddles for it).
 *
 * <p>Our own player aims while the mod's cannon view is on. Of another player only the cannon is
 * seen: they are taken to be aiming for a moment after it has turned. Optional, by name.</p>
 */
public final class CannonAim {

    /** Blocks from the cannon's middle: back along the barrel to the breech, out to a side of it, and up. */
    private static final double BACK = 0.42, SIDE = 0.2, UP = 0.12;
    /** Seconds another player's hands stay on a cannon that has stopped turning. */
    private static final double LINGER = 1.5;

    private static boolean failed;
    private static Method cannonOf, positionOf, facingOf, active;
    /** For each boat: the way its cannon last looked, and when it last turned, nanoseconds. */
    private static final Map<Boat, Object[]> TURNED = new WeakHashMap<>();

    private CannonAim() {
    }

    /** The places of the right hand and the left on the breech of {@code boat}'s cannon, in the world; {@code null} when {@code player} is not aiming it. */
    public static Vec3[] grips(Boat boat, AbstractClientPlayer player, float partial, long now) {
        if (failed || !CannonInBow.moved(boat)) return null;
        try {
            if (cannonOf == null) cannonOf = boat.getClass().getMethod("getInternalCannon");
            Object cannon = cannonOf.invoke(boat);
            if (cannon == null) return null;
            if (positionOf == null) {
                positionOf = cannon.getClass().getMethod("getGlobalPosition", float.class);
                facingOf = cannon.getClass().getMethod("getGlobalFacing", float.class);
                active = Class.forName("net.mehvahdjukaar.supplementaries.client.cannon.CannonController").getMethod("isActive");
            }
            Vector3f facing = new Vector3f((Vector3f) facingOf.invoke(cannon, partial)).normalize();
            // Against the boat, so that the boat turning under a still cannon is not the cannon turning.
            float yaw = (float) Math.toRadians(boat.getViewYRot(partial));
            Vector3f local = new Vector3f(facing).rotateY(yaw);
            Object[] seen = TURNED.computeIfAbsent(boat, b -> new Object[]{new Vector3f(local), 0L});
            if (((Vector3f) seen[0]).distance(local) > 0.01f) {
                ((Vector3f) seen[0]).set(local);
                seen[1] = now;
            }
            boolean aiming = player == Minecraft.getInstance().player ? (Boolean) active.invoke(null)
                    : (Long) seen[1] != 0L && (now - (Long) seen[1]) / 1e9 < LINGER;
            if (!aiming) return null;
            Vec3 at = (Vec3) positionOf.invoke(cannon, partial);
            Vec3 along = new Vec3(facing.x, facing.y, facing.z);
            Vec3 flat = new Vec3(along.x, 0, along.z);
            // The barrel straight up has no side of its own: the boat's then.
            Vec3 ahead = flat.lengthSqr() > 1e-4 ? flat.normalize() : new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 right = new Vec3(-ahead.z, 0, ahead.x);
            Vec3 breech = at.subtract(along.scale(BACK)).add(0, UP, 0);
            return new Vec3[]{breech.add(right.scale(SIDE)), breech.subtract(right.scale(SIDE))};
        } catch (Throwable t) {
            failed = true;
            org.slf4j.LoggerFactory.getLogger("TouchNMotion").warn("[TouchNMotion] could not read a boat's cannon; the hands stay on the paddles", t);
            return null;
        }
    }
}
