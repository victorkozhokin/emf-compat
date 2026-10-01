package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A cauldron: with something it takes in the hand - a bucket, a bottle, a thing to wash in water -
 * the hand goes down to the surface of what is in it. Filled further, the hand pours in; emptied,
 * it scoops out; a dyed thing, a banner or a shulker box washed, it dips it in.
 */
final class Cauldron implements BlockTarget {

    private static final int FULL = 3;
    /** The surface with nothing in it: over the floor (4 px), not down on it - the arm is not that long through the rim. */
    private static final double EMPTY_Y = 9;

    /** The cauldron and whether what the player holds is washed in it - the level going down is then a dip, not a scoop. */
    private record Seen(BlockState block, boolean washes) {
    }

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof AbstractCauldronBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        ItemStack held = player.getMainHandItem();
        int fill = fill(block);
        boolean water = block.is(Blocks.WATER_CAULDRON);
        boolean used = held.is(Items.WATER_BUCKET) || held.is(Items.LAVA_BUCKET) || held.is(Items.POWDER_SNOW_BUCKET)
                || held.is(Items.BUCKET) && fill == FULL
                || held.is(Items.GLASS_BOTTLE) && water
                || waterBottle(held) && (fill == 0 || water && fill < FULL)
                || water && washes(held);
        return used ? surface(pos, block) : null;
    }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        return new Seen(block, block.is(Blocks.WATER_CAULDRON) && washes(player.getMainHandItem()));
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now)) return null;
        int from = fill(before.block), to = fill(now.block);
        if (to > from) return new Gesture(surface(pos, now.block), Motion.PUT);
        if (to < from) return new Gesture(surface(pos, before.block), before.washes ? Motion.PUT : Motion.TAKE);
        return null;
    }

    /** How full, 0..3: water and powder snow by their layers, lava all the way. */
    private static int fill(BlockState block) {
        if (block.is(Blocks.CAULDRON) || !(block.getBlock() instanceof AbstractCauldronBlock)) return 0;
        return block.getBlock() instanceof LayeredCauldronBlock ? block.getValue(LayeredCauldronBlock.LEVEL) : FULL;
    }

    /** The middle of the surface: {@code LayeredCauldronBlock.getContentHeight} - 6 px and 3 a layer; lava at 15. */
    private static Spot surface(BlockPos pos, BlockState block) {
        int fill = fill(block);
        double y = fill == 0 ? EMPTY_Y : block.getBlock() instanceof LayeredCauldronBlock ? 6 + 3 * fill : 15;
        return Spots.top(pos, 8, y, 8);
    }

    private static boolean waterBottle(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return stack.is(Items.POTION) && contents != null && contents.is(Potions.WATER);
    }

    /** What water washes clean: a dyed thing, a banner with a pattern, a coloured shulker box. */
    private static boolean washes(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.has(DataComponents.DYED_COLOR)) return true;
        if (stack.getItem() instanceof BannerItem) {
            return !stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().isEmpty();
        }
        return Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock box && box.getColor() != null;
    }
}
