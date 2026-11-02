package strm.touchnmotion.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a player stands on - the world, or a Sable sub-level - and how they move across it. On a
 * moving sub-level the world position and velocity are mostly the craft's: a player standing at
 * the wheel of a flying ship "moves" a third of a block a tick, and every "is the player still?"
 * turned its feature off. Asked here, standing on a moving deck is standing.
 *
 * <p>On the world's own ground the answers are exactly the ones the game gives ({@code x - xo},
 * {@link Entity#getDeltaMovement()}), so nothing changes there. Worked out once a tick per player.</p>
 */
public final class Anchor {
    /** How long a sub-level stays the support once the player is off it in the air: a jump on the deck. */
    private static final int HOLD_TICKS = 10;
    /**
     * A drop off a deck short enough to be a step on it, not a fall: walking down a heeled craft the
     * deck sinks away under each step and the player drops a pixel or two for a tick or two.
     */
    private static final int FOOTING_TICKS = 3;
    private static final double FOOTING_DROP = 0.3;
    /** How much faster than at take-off the walk may go in the air: what a jump steers, blocks a tick. */
    private static final double AIR_STEER = 0.03;
    public static final String KEY_FOOTING = "sable.footing";

    public static void register(strm.emfcompat.core.ConfigRegistry.Group config) {
        config.addBoolean(KEY_FOOTING, "Steady footing on sub-levels", true,
                "On", "Walking down a heeled or rocking Sable sub-level, the short drops off the deck under each step count as standing on it: the resource pack no longer lands the player again and again, and the legs do not run in the air after a jump on a moving one.",
                "Off", "The resource pack sees every drop off a sub-level's deck, as the game has it.");
        // The core's steady height on sub-levels (1.21.1, where Sable is) holds the height through such a drop.
        try {
            @SuppressWarnings("unchecked")
            var hold = (java.util.List<java.util.function.Predicate<net.minecraft.world.entity.Entity>>)
                    Class.forName("strm.emfcompat.core.SubLevelHeight").getField("HOLD").get(null);
            hold.add(entity -> entity instanceof Player player && footing(player));
        } catch (ReflectiveOperationException | LinkageError absent) {
            // An older core, or a version without Sable.
        }
        // EMF can rename what is hooked; said once whether the hook is there.
        try {
            boolean hooked = false;
            for (java.lang.reflect.Method m : Class.forName("traben.entity_model_features.models.animation.math.EMFMath").getDeclaredMethods())
                hooked |= m.getName().contains("footingOnDeck");
            if (!hooked) LOGGER.warn("[Anchor] EMF's is_on_ground could not be hooked: resource packs see every drop off a sub-level's deck");
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("[Anchor] EMF's is_on_ground could not be looked at", e);
        }
    }

    private static final class State {
        int tick = Integer.MIN_VALUE;
        SubLevels.Space space = SubLevels.WORLD;
        int lastOn = Integer.MIN_VALUE;
        Vec3 local;
        Vec3 motion = Vec3.ZERO;
        /** The last three ticks' moves across the deck, before {@link #motion} picks the middle one. */
        final Vec3[] moves = {Vec3.ZERO, Vec3.ZERO, Vec3.ZERO};
        int moveAt;
        /** Off the ground on a sub-level since this tick; MIN_VALUE on it. */
        int airSince = Integer.MIN_VALUE;
        /** The height on the deck the feet were last on, and whether the player went up off it. */
        double groundY = Double.NaN;
        /** How fast the player went across the deck on their last tick on it, blocks a tick. */
        double groundSpeed;
        boolean jumped, footing;
    }

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("TouchNMotionAnchor");
    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private Anchor() {
    }

    /** The space the player stands on as of this tick: {@link SubLevels#WORLD} on the ground. */
    public static SubLevels.Space space(Player player) {
        return update(player).space.refresh();
    }

    /** Whether the player stands on a sub-level. */
    public static boolean onSubLevel(Player player) {
        return !update(player).space.isWorld();
    }

    /**
     * How far the player went in the last tick, horizontally, in world directions: {@code x - xo}
     * on the ground, across the deck on a sub-level.
     */
    public static Vec3 moved(Player player) {
        State s = update(player);
        return s.space.isWorld() ? new Vec3(player.getX() - player.xo, 0, player.getZ() - player.zo) : s.motion;
    }

    /** {@link #moved}'s length, blocks a tick. */
    public static double speed(Player player) {
        return moved(player).horizontalDistance();
    }

    /**
     * The player's velocity, horizontally: the game's {@code getDeltaMovement()} on the ground,
     * the move across the deck on a sub-level.
     */
    public static Vec3 velocity(Player player) {
        State s = update(player);
        if (!s.space.isWorld()) return s.motion;
        Vec3 v = player.getDeltaMovement();
        return new Vec3(v.x, 0, v.z);
    }

    /**
     * Whether the player, off the ground, is still to be taken as standing on the sub-level: a short
     * drop off its deck that is a step down it, not a jump or a fall.
     */
    public static boolean footing(Player player) {
        return !player.onGround() && update(player).footing
                && strm.emfcompat.core.EMFCompatConfig.getBoolean(KEY_FOOTING, true);
    }

    /**
     * How far the player walked this tick for the walk animation. Sable counts it across the deck
     * while it carries the player; off the deck - a jump - it lets go and the game counts the
     * craft's own speed, so the legs run in the air. While the sub-level is still the player's
     * support, it is counted across the deck here too.
     */
    public static float walkDistance(Player player, float distance) {
        State s = update(player);
        // Sable lets go of the player and takes them back a tick out of step with the craft: its own
        // count jumps by the craft's speed on those ticks, on the deck as in the air.
        if (s.space.isWorld() || !strm.emfcompat.core.EMFCompatConfig.getBoolean(KEY_FOOTING, true)) return distance;
        double across = s.motion.horizontalDistance();
        // In the air no faster than the player left the deck, but for the little a jump steers: that
        // hand-over jump can last two ticks, past what the middle of three takes out.
        return (float) (player.onGround() ? across : Math.min(across, s.groundSpeed + AIR_STEER));
    }

    private static boolean saidFailed;

    /** A failure where the game or EMF asked: said once, the game's own answer kept. */
    public static void failed(RuntimeException e) {
        if (saidFailed) return;
        saidFailed = true;
        LOGGER.warn("[Anchor] the support on a sub-level could not be worked out; the game's own answer is kept", e);
    }

    private static State update(Player player) {
        State s = STATES.seen(player.getUUID(), System.nanoTime()).value;
        if (s.tick == player.tickCount) return s;
        boolean fresh = s.tick == Integer.MIN_VALUE || player.tickCount - s.tick > 5;
        s.tick = player.tickCount;
        SubLevels.Space found = find(player);
        if (found.isWorld() && !s.space.isWorld() && s.space.valid() && !player.onGround()
                && player.tickCount - s.lastOn <= HOLD_TICKS) found = s.space.refresh();
        if (!found.isWorld() && player.onGround()) s.lastOn = player.tickCount;
        if (!found.same(s.space) && strm.touchnmotion.DebugLog.decisions())
            LOGGER.info("[Anchor] {} stands on {}", player.getName().getString(), found.isWorld() ? "the world" : found);
        if (found.isWorld()) {
            s.space = SubLevels.WORLD;
            s.local = null;
            s.motion = Vec3.ZERO;
            s.groundY = Double.NaN;
            s.airSince = Integer.MIN_VALUE;
            s.footing = false;
            return s;
        }
        Vec3 local = found.tickToLocal(player.position());
        if (!found.same(s.space)) s.groundY = Double.NaN;
        footing(player, s, local);
        if (s.local != null && found.same(s.space)) {
            Vec3 moved = found.tickDirectionToWorld(local.subtract(s.local));
            s.motion = steady(s, new Vec3(moved.x, 0, moved.z));
        } else if (fresh) {
            // First seen already on it: nothing to compare with yet, and the world move is the craft's.
            s.motion = Vec3.ZERO;
        } else {
            // Just stepped on: how they came, in the world, is the best there is for one tick.
            s.motion = new Vec3(player.getX() - player.xo, 0, player.getZ() - player.zo);
        }
        if (s.local == null || !found.same(s.space)) java.util.Arrays.fill(s.moves, s.motion);
        if (player.onGround()) s.groundSpeed = s.motion.horizontalDistance();
        s.space = found;
        s.local = local;
        return s;
    }

    /**
     * Keeps track of the feet leaving the deck: up off it is a jump, down off it a step down the deck
     * while it is short and shallow.
     */
    private static void footing(Player player, State s, Vec3 local) {
        if (player.onGround()) {
            s.airSince = Integer.MIN_VALUE;
            s.groundY = local.y;
            s.jumped = false;
            s.footing = false;
            return;
        }
        if (s.airSince == Integer.MIN_VALUE) {
            s.airSince = player.tickCount;
            s.jumped = player.getDeltaMovement().y > 0.01 || Double.isNaN(s.groundY) || local.y > s.groundY + 0.01;
        }
        s.footing = !s.jumped && player.tickCount - s.airSince < FOOTING_TICKS && s.groundY - local.y <= FOOTING_DROP;
    }

    /**
     * The move across the deck with a lone jump taken out: when Sable lets go of the player for a
     * jump, or takes them back on landing, their place on the deck jumps by the craft's speed for one
     * tick though they hardly moved. The middle of the last three moves keeps a real walk a tick late.
     */
    private static Vec3 steady(State s, Vec3 move) {
        s.moves[s.moveAt] = move;
        s.moveAt = (s.moveAt + 1) % 3;
        Vec3 a = s.moves[0], b = s.moves[1], c = s.moves[2];
        double la = a.lengthSqr(), lb = b.lengthSqr(), lc = c.lengthSqr();
        if ((la >= lb) == (la <= lc)) return a;
        if ((lb >= la) == (lb <= lc)) return b;
        return c;
    }

    /** The sub-level Sable carries the player with, else one with a block right under the feet. */
    private static SubLevels.Space find(Player player) {
        SubLevels.Space tracked = SubLevels.tracking(player);
        if (!tracked.isWorld()) return tracked;
        Vec3 feet = player.position();
        for (SubLevels.Space space : SubLevels.around(player.level(), new AABB(feet, feet).inflate(1, 1.5, 1))) {
            if (space.isWorld()) continue;
            for (double dy : new double[]{0.1, 0.6}) {
                Vec3 below = space.tickToLocal(feet.subtract(0, dy, 0));
                if (!player.level().getBlockState(BlockPos.containing(below)).getCollisionShape(player.level(), BlockPos.containing(below)).isEmpty())
                    return space;
            }
        }
        return SubLevels.WORLD;
    }
}
