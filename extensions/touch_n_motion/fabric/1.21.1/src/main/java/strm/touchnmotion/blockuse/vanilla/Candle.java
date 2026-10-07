package strm.touchnmotion.blockuse.vanilla;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import strm.touchnmotion.blockuse.*;

/**
 * Bring a lighter to the wick or cover a lit candle with an empty hand. Of several candles in the
 * block the hand goes to the tallest one's wick (the candle models' own: {@code template_candle},
 * {@code template_two_candles} and so on).
 */
public final class Candle implements BlockTarget {
    public boolean matches(BlockState block) { return block.getBlock() instanceof CandleBlock; }

    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        boolean lit = block.getValue(CandleBlock.LIT);
        var held = player.getMainHandItem();
        boolean canLight = !block.getValue(CandleBlock.WATERLOGGED)
                && (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE));
        return lit ? held.isEmpty() ? wick(pos, block) : null : canLight ? wick(pos, block) : null;
    }

    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now)
                || !matches(before) || !matches(now)) return null;
        return before.getValue(CandleBlock.LIT) != now.getValue(CandleBlock.LIT)
                ? new Gesture(wick(pos, now), Motion.TAP) : null;
    }

    /** The tallest candle's wick, pixels, for one to four candles. */
    private static final double[][] WICKS = {{8, 7, 8}, {10, 7, 7}, {9, 7, 7}, {9, 7, 6}};

    private static Spot wick(BlockPos pos, BlockState block) {
        double[] wick = WICKS[block.getValue(CandleBlock.CANDLES) - 1];
        return Spots.top(pos, wick[0], wick[1], wick[2]);
    }
}
