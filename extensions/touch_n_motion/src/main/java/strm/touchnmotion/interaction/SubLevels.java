package strm.touchnmotion.interaction;

import java.util.Map;
import java.util.IdentityHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Sable's sub-levels (Create Aeronautics' craft): an assembled craft's blocks are kept in a plot
 * millions of blocks out and drawn moved and turned by the craft's pose. A block found there is
 * worked with in the plot's own coordinates - its shape, its facing, where the player stands
 * against it - and only the points the hands go to are carried out into the world.
 *
 * <p>Optional, by reflection: without Sable everything is {@link #WORLD}. Positions near a plot
 * are past a float's precision, so everything here is double.</p>
 */
public final class SubLevels {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatSubLevels");

    /** Where blocks are: the world itself, or one sub-level's plot seen through its pose this frame. */
    public static final class Space {
        private final Object pose;
        private final Object sub;

        private Space(Object pose, Object sub) {
            this.pose = pose;
            this.sub = sub;
        }

        /** Stable identity and a fresh render transform for a retained contact. */
        public boolean same(Space other) { return other != null && sub == other.sub; }
        public boolean valid() { return sub == null || !(boolean) call(removed, sub); }
        public Space refresh() { return sub == null ? this : space(sub); }
        public Vec3 tickToWorld(Vec3 local) {
            return sub == null ? local : (Vec3) call(toWorld, call(logicalPose, sub), local);
        }

        public Vec3 tickToLocal(Vec3 world) {
            return sub == null ? world : (Vec3) call(toLocal, call(logicalPose, sub), world);
        }

        public boolean isWorld() {
            return pose == null;
        }

        /** A point of this space in the world. */
        public Vec3 toWorld(Vec3 local) {
            return pose == null ? local : (Vec3) call(toWorld, pose, local);
        }

        /** A world point in this space. */
        public Vec3 toLocal(Vec3 world) {
            return pose == null ? world : (Vec3) call(toLocal, pose, world);
        }

        /** A direction of this space in the world. */
        public Vec3 directionToWorld(Vec3 local) {
            return pose == null ? local : (Vec3) call(normalToWorld, pose, local);
        }

        /** A world direction in this space. */
        public Vec3 directionToLocal(Vec3 world) {
            return pose == null ? world : (Vec3) call(normalToLocal, pose, world);
        }

        /**
         * The blocks of this space along a world ray, {@code from} to {@code to}; a miss when there is
         * nothing or the ray cannot be carried into this space.
         */
        public BlockHitResult clip(Level level, Vec3 from, Vec3 to, ClipContext.Block block, Entity entity) {
            return clipLocal(level, toLocal(from), toLocal(to), from.distanceTo(to), block, entity);
        }

        /**
         * {@link #clip} with the ray already in this space; {@code worldLength} is how long it is in the
         * world. A ray the pose stretches far past that is not cast: the game walks a ray block by
         * block, and one stretched across a plot (millions of blocks) froze the game.
         */
        public BlockHitResult clipLocal(Level level, Vec3 from, Vec3 to, double worldLength,
                                        ClipContext.Block block, Entity entity) {
            double length = from.distanceTo(to);
            if (!Double.isFinite(length) || length > worldLength * 8 + 2) {
                stretched(this, from, to, worldLength);
                return BlockHitResult.miss(to, net.minecraft.core.Direction.UP, BlockPos.containing(to));
            }
            BlockHitResult hit = level.clip(new ClipContext(from, to, block, ClipContext.Fluid.NONE, entity));
            // Sable lets a ray cast in the world hit a sub-level too, and answers in the sub-level's plot:
            // millions of blocks out, taken for a world point it sent a hand there (#63). The
            // sub-level's own space finds that block where it is.
            if (pose == null && hit.getType() == HitResult.Type.BLOCK && !at(level, hit.getBlockPos()).isWorld())
                return BlockHitResult.miss(to, hit.getDirection(), BlockPos.containing(to));
            return hit;
        }

        @Override
        public String toString() {
            return pose == null ? "world" : String.valueOf(pose);
        }
    }

    private static boolean saidStretched;

    /** A ray a sub-level's pose stretched, said once a game: what pose did it. */
    private static void stretched(Space space, Vec3 from, Vec3 to, double worldLength) {
        if (saidStretched) return;
        saidStretched = true;
        LOGGER.warn("[SubLevels] a ray of {} blocks became {} in a sub-level and was not cast ({} -> {}); pose {}",
                worldLength, from.distanceTo(to), from, to, space);
    }

    public static final Space WORLD = new Space(null, null);

    private static boolean looked, absent;
    private static Object helper;
    private static Method containing, intersecting, renderPose, toWorld, toLocal, normalToWorld, normalToLocal, logicalPose, removed;
    private static Constructor<?> box;

    private SubLevels() {
    }

    /** The space the block at {@code pos} is in. */
    public static Space at(Level level, BlockPos pos) {
        if (!ready()) return WORLD;
        Object sub = call(containing, helper, level, pos);
        return sub == null ? WORLD : space(sub);
    }

    /**
     * Where a ray cast in the world hit, in the world. Sable lets such a ray hit a sub-level and
     * answers in the sub-level's plot, millions of blocks out.
     */
    public static Vec3 hitInWorld(Level level, net.minecraft.world.phys.HitResult hit) {
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? at(level, block.getBlockPos()).toWorld(hit.getLocation()) : hit.getLocation();
    }

    /** A point of the block at {@code pos}'s space, in the world. */
    public static Vec3 toWorld(Level level, BlockPos pos, Vec3 point) {
        return at(level, pos).toWorld(point);
    }

    /** The world, and every sub-level whose bounds come within {@code area} (world). */
    public static List<Space> around(Level level, AABB area) {
        List<Space> spaces = new ArrayList<>(2);
        spaces.add(WORLD);
        if (!ready()) return spaces;
        Object bounds = newInstance(box, area);
        for (Object sub : (Iterable<?>) call(intersecting, helper, level, bounds)) spaces.add(space(sub));
        return spaces;
    }

    /** Each craft's space this frame: every provider asks for the same few, the pose is worked out once. */
    private static final Map<Object, Space> SPACES = new IdentityHashMap<>();
    private static long spacesTick = Long.MIN_VALUE;
    private static float spacesPartial = Float.NaN;

    private static Space space(Object sub) {
        Minecraft mc = Minecraft.getInstance();
        float partial = strm.touchnmotion.platform.Platform.partialTick(false);
        long tick = mc.level == null ? Long.MIN_VALUE : mc.level.getGameTime();
        if (tick != spacesTick || partial != spacesPartial) {
            SPACES.clear();
            spacesTick = tick;
            spacesPartial = partial;
        }
        return SPACES.computeIfAbsent(sub, s -> new Space(call(renderPose, s, partial), s));
    }

    private static boolean ready() {
        if (!looked) {
            looked = true;
            try {
                helper = Class.forName("dev.ryanhcode.sable.Sable").getField("HELPER").get(null);
                Class<?> boxClass = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3d");
                Class<?> boxInterface = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3dc");
                Class<?> pose = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
                containing = helper.getClass().getMethod("getContaining", Level.class, Vec3i.class);
                intersecting = helper.getClass().getMethod("getAllIntersecting", Level.class, boxInterface);
                box = boxClass.getConstructor(AABB.class);
                renderPose = Class.forName("dev.ryanhcode.sable.sublevel.ClientSubLevel").getMethod("renderPose", float.class);
                Class<?> subClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                logicalPose = subClass.getMethod("logicalPose");
                removed = subClass.getMethod("isRemoved");
                toWorld = pose.getMethod("transformPosition", Vec3.class);
                toLocal = pose.getMethod("transformPositionInverse", Vec3.class);
                normalToWorld = pose.getMethod("transformNormal", Vec3.class);
                normalToLocal = pose.getMethod("transformNormalInverse", Vec3.class);
            } catch (ClassNotFoundException missing) {
                absent = true;
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("[SubLevels] Sable is there but its API is not as expected; craft are left out", e);
                absent = true;
            }
        }
        return !absent;
    }

    private static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Sable " + method.getName() + " failed", e);
        }
    }

    private static Object newInstance(Constructor<?> constructor, Object... args) {
        try {
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Sable bounding box failed", e);
        }
    }
}
