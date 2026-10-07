package strm.emfcompat.animationadditions.blockuse.vanilla;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import strm.emfcompat.animationadditions.blockuse.*;

/**
 * TNT: with a lighter in the hand - flint and steel, a fire charge - the hand brings it to the fuse
 * on the top; lit, the block is gone (it is an entity from then on) and the hand strikes there.
 */
public final class Tnt implements BlockTarget {

    /** The block and whether the player holds a lighter: the block going is then a lighting, not a breaking. */
    private record Seen(BlockState block, boolean lighter) {
    }

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof TntBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return lighter(player.getMainHandItem()) ? fuse(pos) : null;
    }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        return new Seen(block, lighter(player.getMainHandItem()));
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        // Lit, it is no TNT block any more: what is seen then is the bare state, air.
        if (!(was instanceof Seen before) || !before.lighter || !(is instanceof BlockState now) || !now.isAir()) return null;
        return new Gesture(fuse(pos), Motion.TAP);
    }

    private static boolean lighter(ItemStack stack) {
        return stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE);
    }

    private static Spot fuse(BlockPos pos) {
        return Spots.top(pos, 8, 16, 8);
    }
}
