package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One kind of block the hand uses, for {@link BlockUse}: where the hand goes on it while it is
 * looked at, and what the hand did when it changed.
 */
public interface BlockTarget {

    /** Where on a block the hand is: a point on its surface and the way out of the block there (a unit vector). */
    record Spot(Vec3 point, Vec3 out) {
    }

    /** What the hand does: puts something in, takes it out, taps, or stays down on the spot - drawing. */
    enum Motion {PUT, TAKE, TAP, HOLD}

    /**
     * What the hand does and where; {@code sweep}, when not {@code null}, is how far and which way
     * it also goes across the spot while it does it - a push along a surface.
     */
    record Gesture(Spot spot, Motion motion, Vec3 sweep) {
        Gesture(Spot spot, Motion motion) {
            this(spot, motion, null);
        }
    }

    boolean matches(BlockState block);

    /**
     * Where the hand waits, looked at where {@code hit} is; {@code null} when a click there would
     * do nothing - the hand only goes to what it can use.
     */
    Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit);

    /** Optional other-hand contact; both hands are arbitrated together. */
    default Spot supportHand(AbstractClientPlayer player, BlockPos pos, BlockState block) { return null; }

    /**
     * When the torso goes with the hand as it moves round - a crank's grip - the point it goes round,
     * world; {@code null} for a still torso. The torso leans the way the hand is off it.
     */
    default Vec3 swayCentre(Level level, BlockPos pos, BlockState block) {
        return null;
    }

    /** Rendered rotation in degrees for grounded setup steps; null for other interactions. */
    default Float stanceAngle(Level level, BlockPos pos) { return null; }

    /**
     * Whether the player is at work on the block and the hands stay on it wherever the look goes -
     * typing on a typewriter. Asked of the block the hands are on already.
     */
    default boolean holds(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        return false;
    }

    /** Whether the body reaches with the hand past the arm's length, as for a lever ({@code ReachPose}). */
    default boolean reachPose() {
        return false;
    }

    /** Wheels at hand height need contact correction, not the one-legged counterbalance of a long reach. */
    default boolean balancesReach() { return reachPose(); }

    /**
     * Whether the swing a click starts is kept off the body while the hand is on it - a crank held
     * down starts one after another, and the pack's swing twists the torso and the other arm.
     */
    default boolean quietsSwing() {
        return false;
    }

    /**
     * What is watched for a change: the block's state, or more - what a block entity holds, when
     * using it does not change the state. Compared with {@code equals}.
     */
    default Object snapshot(Level level, BlockPos pos, BlockState block) {
        return block;
    }

    /** {@link #snapshot} as this player sees it - their own open menu, say, which no one else's is. */
    default Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        return snapshot(level, pos, block);
    }

    /**
     * The block went from {@code before} to {@code now} (two {@link #snapshot}s): where the hand did
     * it and how; {@code null} when not a hand's doing.
     */
    Gesture changed(BlockPos pos, Object before, Object now);
}
