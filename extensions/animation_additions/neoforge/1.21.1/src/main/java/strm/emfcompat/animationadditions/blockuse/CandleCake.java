package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A cake with a candle: a lighter goes to the wick of the unlit candle; an empty hand covers the
 * lit one, looked at over the cake's top (as {@code CandleCakeBlock.candleHit}); otherwise, hungry,
 * the hand goes to the cake - the first slice takes the candle off, and the block is a plain cake
 * from then on.
 */
final class CandleCake implements BlockTarget {

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof CandleCakeBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        boolean lit = block.getValue(CandleCakeBlock.LIT);
        ItemStack held = player.getMainHandItem();
        if (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE)) return lit ? null : wick(pos);
        if (lit && held.isEmpty() && hit.getLocation().y - hit.getBlockPos().getY() > 0.5) return wick(pos);
        return player.canEat(false) ? Spots.top(pos, 8, 8, 8) : null;
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now) || !matches(before)) return null;
        // Eaten from: a cake with its first slice gone.
        if (now.getBlock() instanceof CakeBlock) return new Gesture(Spots.top(pos, 2, 8, 8), Motion.TAKE);
        if (!matches(now)) return null;
        return before.getValue(CandleCakeBlock.LIT) != now.getValue(CandleCakeBlock.LIT)
                ? new Gesture(wick(pos), Motion.TAP) : null;
    }

    /** The candle stands on the cake's top, 8 px up: its wick ends at 15. */
    private static Spot wick(BlockPos pos) {
        return Spots.top(pos, 8, 15, 8);
    }
}
