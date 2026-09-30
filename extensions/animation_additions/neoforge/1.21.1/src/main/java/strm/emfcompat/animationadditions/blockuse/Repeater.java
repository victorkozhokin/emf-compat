package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A repeater: the hand waits on the torch that sets the delay and moves it along when a click
 * changes the delay. The model ({@code repeater_Ntick}, facing south) has that torch 2 pixels
 * across, its middle at z 7, 9, 11, 13 for a delay of 1 to 4, 7 pixels tall.
 */
final class Repeater implements BlockTarget {

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof RepeaterBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return torch(pos, block);
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now) || !matches(before) || !matches(now)) return null;
        return !before.getValue(RepeaterBlock.DELAY).equals(now.getValue(RepeaterBlock.DELAY))
                ? new Gesture(torch(pos, now), Motion.TAP) : null;
    }

    private static Spot torch(BlockPos pos, BlockState block) {
        int delay = block.getValue(RepeaterBlock.DELAY);
        return Spots.turned(pos, block.getValue(RepeaterBlock.FACING), 8, 7, 5 + 2 * delay);
    }
}
