package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Bring a lighter to the wick or cover a lit candle with an empty hand. */
final class Candle implements BlockTarget {
    public boolean matches(BlockState block) { return block.getBlock() instanceof CandleBlock; }

    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        boolean lit = block.getValue(CandleBlock.LIT);
        var held = player.getMainHandItem();
        boolean canLight = !block.getValue(CandleBlock.WATERLOGGED)
                && (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE));
        return lit ? held.isEmpty() ? wick(pos) : null : canLight ? wick(pos) : null;
    }

    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now)
                || !matches(before) || !matches(now)) return null;
        return before.getValue(CandleBlock.LIT) != now.getValue(CandleBlock.LIT)
                ? new Gesture(wick(pos), Motion.TAP) : null;
    }

    private static Spot wick(BlockPos pos) { return Spots.top(pos, 8, 7, 8); }
}
