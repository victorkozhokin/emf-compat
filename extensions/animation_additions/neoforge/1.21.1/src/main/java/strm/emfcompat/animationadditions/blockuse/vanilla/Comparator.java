package strm.emfcompat.animationadditions.blockuse.vanilla;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import strm.emfcompat.animationadditions.blockuse.*;

/**
 * A comparator: the hand waits on the front torch and switches it when a click changes the mode.
 * The model ({@code comparator}, facing south) has it at (8, 3) pixels, 4 tall.
 */
public final class Comparator implements BlockTarget {

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof ComparatorBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return torch(pos, block);
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now) || !matches(before) || !matches(now)) return null;
        return before.getValue(ComparatorBlock.MODE) != now.getValue(ComparatorBlock.MODE)
                ? new Gesture(torch(pos, now), Motion.TAP) : null;
    }

    private static Spot torch(BlockPos pos, BlockState block) {
        return Spots.turned(pos, block.getValue(ComparatorBlock.FACING), 8, 4, 3);
    }
}
