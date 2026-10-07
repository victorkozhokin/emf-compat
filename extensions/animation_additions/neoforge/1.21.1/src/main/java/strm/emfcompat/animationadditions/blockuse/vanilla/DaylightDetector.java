package strm.emfcompat.animationadditions.blockuse.vanilla;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import strm.emfcompat.animationadditions.blockuse.*;

/** A daylight detector: the hand waits over the panel and pats it when a click turns it over (inverted). */
public final class DaylightDetector implements BlockTarget {

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof DaylightDetectorBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return panel(pos);
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now) || !matches(before) || !matches(now)) return null;
        return before.getValue(DaylightDetectorBlock.INVERTED) != now.getValue(DaylightDetectorBlock.INVERTED)
                ? new Gesture(panel(pos), Motion.TAP) : null;
    }

    /** The middle of the panel, 6 pixels up. */
    private static Spot panel(BlockPos pos) {
        return Spots.top(pos, 8, 6, 8);
    }
}
