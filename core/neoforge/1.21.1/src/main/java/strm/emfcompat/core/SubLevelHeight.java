package strm.emfcompat.core;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The height EMF gives a resource pack as {@code pos_y}, without a Sable sub-level's own rise and fall.
 *
 * <p>Packs read {@code pos_y} frame to frame to tell a fall, a landing or a vertical speed - Fresh
 * Animations restarts its landing whenever a player on the ground goes down, as off a step. On a
 * sub-level going down every frame is such a step, so the landing played over and over and the body
 * swayed with every rise. Here the height goes up and down with the sub-level's own up, not the
 * world's: the sub-level rising or sinking under the entity is taken out, and so is its heel - walking
 * down a heeled deck the world height sinks with every step though the feet stay on the same planks.
 * The pack sees the entity move on the sub-level, as it would on the ground.</p>
 *
 * <p>What has been taken out is kept after the entity steps off, so the height does not jump by how
 * far the sub-level travelled meanwhile (a jump a pack would take for a fall). It is dropped with a
 * new level or a teleport, where the height jumps anyway. Gliding with an elytra nothing is taken
 * out: the flight is the entity's own.</p>
 *
 * <p>On the sub-level the height is also held through changes smaller than {@link #STILL}: the entity
 * shifts by a few thousandths against a tilting sub-level, and a pack reads any drop at all as a step
 * down. A step, a jump or a fall goes past it at once.</p>
 */
public final class SubLevelHeight {

    public static final String KEY_ENABLED = "core.sableSteadyHeight";

    /** A change of height this large between two frames is a teleport: what was taken out starts over. */
    private static final double TELEPORT = 8;
    /** Height changes on a sub-level below this, blocks, are not passed on. */
    private static final double STILL = 0.04;

    private SubLevelHeight() {
    }

    /** Sable's calls, looked up once; all null without Sable. */
    private static final class Api {
        static final Method TRACKING, RENDER_POSE, TO_LOCAL;

        static {
            Method tracking = null, renderPose = null, toLocal = null;
            try {
                tracking = Class.forName("dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension")
                        .getMethod("sable$getTrackingSubLevel");
                renderPose = Class.forName("dev.ryanhcode.sable.sublevel.ClientSubLevel").getMethod("renderPose", float.class);
                Class<?> pose = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
                toLocal = pose.getMethod("transformPositionInverse", Vec3.class);
            } catch (Throwable absent) {
                tracking = null;
            }
            TRACKING = tracking;
            RENDER_POSE = renderPose;
            TO_LOCAL = toLocal;
        }
    }

    private static final class Ride {
        Level level;
        Object sub;
        /** Where the entity was on the sub-level last frame, and its world height then. */
        Vec3 local;
        double localY;
        /** How far the world height has gone up or down beyond the height up the sub-level, blocks. */
        double taken;
        double lastY = Double.NaN;
        /** The height given out while on the sub-level. */
        double held = Double.NaN;
        float frame = Float.NaN;
    }

    /**
     * What an addon says holds the height where it is on a sub-level: a short drop off its deck it
     * takes for a step on it (Touch'n Motion's steady footing), where the pack must not see a fall.
     */
    public static final java.util.List<java.util.function.Predicate<Entity>> HOLD = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final Map<Entity, Ride> RIDES = new WeakHashMap<>();
    private static boolean failed;

    /**
     * {@code y} is EMF's {@code pos_y} for {@code entity}, at {@code partialTick}; returns it with the
     * sub-level's movement taken out. Works it out once a frame per entity, whatever reads it.
     */
    public static float adjust(Entity entity, float y, float partialTick, float frame) {
        if (failed || Api.TRACKING == null || entity == null
                || !EMFCompatCore.isCompatEnabled() || !EMFCompatConfig.getBoolean(KEY_ENABLED, true)) return y;
        Ride ride = RIDES.computeIfAbsent(entity, e -> new Ride());
        if (frame != ride.frame) {
            ride.frame = frame;
            try {
                update(ride, entity, y, partialTick);
            } catch (Throwable t) {
                failed = true;
                org.slf4j.LoggerFactory.getLogger("EMFCompatCore")
                        .warn("[SubLevelHeight] Sable is there but not as expected; pos_y is left as it is", t);
                return y;
            }
        }
        double steady = y - ride.taken;
        if (ride.sub == null) {
            ride.held = Double.NaN;
            return (float) steady;
        }
        if (Double.isNaN(ride.held)) ride.held = steady;
        else if (held(entity)) return (float) ride.held;
        else if (steady > ride.held + STILL) ride.held = steady - STILL;
        else if (steady < ride.held - STILL) ride.held = steady + STILL;
        return (float) ride.held;
    }

    private static boolean held(Entity entity) {
        for (var hold : HOLD) if (hold.test(entity)) return true;
        return false;
    }

    private static void update(Ride ride, Entity entity, float y, float partialTick) throws ReflectiveOperationException {
        if (ride.level != entity.level() || !Double.isNaN(ride.lastY) && Math.abs(y - ride.lastY) > TELEPORT) {
            ride.level = entity.level();
            ride.sub = null;
            ride.taken = 0;
        }
        ride.lastY = y;
        Object sub = Api.TRACKING.invoke(entity);
        if (sub == null || !Api.RENDER_POSE.getDeclaringClass().isInstance(sub)) {
            ride.sub = null;
            return;
        }
        Object pose = Api.RENDER_POSE.invoke(sub, partialTick);
        Vec3 at = entity.getPosition(partialTick);
        at = new Vec3(at.x, y, at.z);
        Vec3 local = (Vec3) Api.TO_LOCAL.invoke(pose, at);
        if (sub == ride.sub && ride.local != null) {
            // What the world height did since last frame beyond the height up the sub-level: its rise
            // and sink, and its heel under steps along it.
            boolean gliding = entity instanceof LivingEntity living && living.isFallFlying();
            if (!gliding) ride.taken += (y - ride.localY) - (local.y - ride.local.y);
        }
        ride.sub = sub;
        ride.local = local;
        ride.localY = y;
    }
}
