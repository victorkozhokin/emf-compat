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

    /** What the hand does: puts something in, takes it out, or taps. */
    enum Motion {PUT, TAKE, TAP}

    record Gesture(Spot spot, Motion motion) {
    }

    boolean matches(BlockState block);

    /**
     * Where the hand waits, looked at where {@code hit} is; {@code null} when a click there would
     * do nothing - the hand only goes to what it can use.
     */
    Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit);

    /**
     * What is watched for a change: the block's state, or more - what a block entity holds, when
     * using it does not change the state. Compared with {@code equals}.
     */
    default Object snapshot(Level level, BlockPos pos, BlockState block) {
        return block;
    }

    /**
     * The block went from {@code before} to {@code now} (two {@link #snapshot}s): where the hand did
     * it and how; {@code null} when not a hand's doing.
     */
    Gesture changed(BlockPos pos, Object before, Object now);
}
