package strm.touchnmotion.blockuse.vanilla;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import strm.touchnmotion.blockuse.*;

/**
 * A composter: with something that composts in the hand, the hand goes over the opening and puts
 * it in when the level goes up; filling it (level 7) it presses the heap down from above; ready
 * (level 8), the hand takes the bone meal out. A try that does not raise the level changes nothing
 * the hand can be told by.
 */
public final class Composter implements BlockTarget {

    private static final int FULL = 7, READY = 8;

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof ComposterBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        int level = block.getValue(ComposterBlock.LEVEL);
        if (level == READY || level < FULL && compostable(player.getMainHandItem())) return opening(pos);
        return null;
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now) || !matches(before) || !matches(now)) return null;
        int from = before.getValue(ComposterBlock.LEVEL), to = now.getValue(ComposterBlock.LEVEL);
        if (from == READY && to == 0) return new Gesture(opening(pos), Motion.TAKE);
        if (to > from && to <= FULL) return new Gesture(opening(pos), to == FULL ? Motion.TAP : Motion.PUT);
        return null;
    }

    private static boolean compostable(ItemStack stack) {
        return !stack.isEmpty() && ComposterBlock.COMPOSTABLES.containsKey(stack.getItem());
    }

    /** The middle of the opening, just under the rim. */
    private static Spot opening(BlockPos pos) {
        return Spots.top(pos, 8, 14, 8);
    }
}
