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

    private static final class State {
        int tick = Integer.MIN_VALUE;
        SubLevels.Space space = SubLevels.WORLD;
        int lastOn = Integer.MIN_VALUE;
        Vec3 local;
        Vec3 motion = Vec3.ZERO;
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
            return s;
        }
        Vec3 local = found.tickToLocal(player.position());
        if (s.local != null && found.same(s.space)) {
            Vec3 moved = found.tickDirectionToWorld(local.subtract(s.local));
            s.motion = new Vec3(moved.x, 0, moved.z);
        } else if (fresh) {
            // First seen already on it: nothing to compare with yet, and the world move is the craft's.
            s.motion = Vec3.ZERO;
        } else {
            // Just stepped on: how they came, in the world, is the best there is for one tick.
            s.motion = new Vec3(player.getX() - player.xo, 0, player.getZ() - player.zo);
        }
        s.space = found;
        s.local = local;
        return s;
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
