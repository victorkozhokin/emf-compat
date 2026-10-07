package strm.emfcompat.animationadditions.blockuse.vanilla;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import strm.emfcompat.animationadditions.blockuse.*;

/**
 * A cake: when the player can eat, the hand goes to what is left of it and takes the next slice
 * as one is eaten. The cake is eaten from its west side: {@code bites} slices of 2 pixels gone
 * from x 1, what is left from 1 + 2 * bites to 15, 8 pixels tall. The last slice takes the block
 * away: no gesture for that one.
 */
public final class Cake implements BlockTarget {

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof CakeBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        if (!player.canEat(false)) return null;
        int bites = block.getValue(CakeBlock.BITES);
        // The middle of what is left.
        return Spots.top(pos, (1 + 2 * bites + 15) / 2.0, 8, 8);
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now) || !matches(before) || !matches(now)) return null;
        int from = before.getValue(CakeBlock.BITES);
        if (now.getValue(CakeBlock.BITES) <= from) return null;
        // The slice taken: the 2 pixels at the cut.
        return new Gesture(Spots.top(pos, 2 + 2 * from, 8, 8), Motion.TAKE);
    }
}
