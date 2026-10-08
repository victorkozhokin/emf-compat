package strm.touchnmotion.blockuse.vanilla;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import strm.touchnmotion.blockuse.*;

/**
 * A flower pot: the hand goes to the earth in it when a click would plant something - an empty pot
 * and a plant that goes in it in the hand - or take the plant out - a full pot and anything else in
 * the hand - as {@code FlowerPotBlock.useItemOn} decides. Planted, the hand puts it in; emptied,
 * it pulls it out. Each plant is a block of its own: any pot matches.
 */
public final class FlowerPot implements BlockTarget {

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof FlowerPotBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        // useItemOn does something when "the item is no plant" and "the pot is empty" differ.
        return plantable(player.getMainHandItem()) == empty(block) ? earth(pos) : null;
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now) || !matches(before) || !matches(now)) return null;
        if (empty(before) && !empty(now)) return new Gesture(earth(pos), Motion.PUT);
        if (!empty(before) && empty(now)) return new Gesture(earth(pos), Motion.TAKE);
        return null;
    }

    private static boolean empty(BlockState block) {
        return strm.touchnmotion.platform.Platform.pottedIn((FlowerPotBlock) block.getBlock()) == Blocks.AIR;
    }

    private static boolean plantable(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return false;
        return strm.touchnmotion.platform.Platform.potted(item.getBlock());
    }

    /** The earth in the pot: its top is 4 pixels up, the rim 6. */
    private static Spot earth(BlockPos pos) {
        return Spots.top(pos, 8, 5, 8);
    }
}
