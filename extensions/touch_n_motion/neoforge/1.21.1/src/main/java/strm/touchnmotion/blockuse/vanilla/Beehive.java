package strm.touchnmotion.blockuse.vanilla;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import strm.touchnmotion.blockuse.*;

/**
 * A beehive or a bee nest full of honey: with a bottle the hand holds it under the entrance in the
 * front and takes the honey; with shears it goes to the combs and cuts them off.
 */
public final class Beehive implements BlockTarget {

    /** On the front, blocks up: the entrance the honey drips from, and the combs over it. */
    private static final double ENTRANCE_Y = 5 / 16.0;
    private static final double COMBS_Y = 9 / 16.0;

    /** The hive and whether the player holds shears: the honey going is then a cut, not a bottle filled. */
    private record Seen(BlockState block, boolean shears) {
    }

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof BeehiveBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        if (block.getValue(BeehiveBlock.HONEY_LEVEL) < BeehiveBlock.MAX_HONEY_LEVELS) return null;
        if (player.getMainHandItem().is(Items.SHEARS)) return front(pos, block, COMBS_Y);
        return player.getMainHandItem().is(Items.GLASS_BOTTLE) ? front(pos, block, ENTRANCE_Y) : null;
    }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        return new Seen(block, player.getMainHandItem().is(Items.SHEARS));
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now)) return null;
        boolean emptied = before.block.getValue(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.MAX_HONEY_LEVELS
                && now.block.getValue(BeehiveBlock.HONEY_LEVEL) == 0;
        if (!emptied) return null;
        return before.shears ? new Gesture(front(pos, now.block, COMBS_Y), Motion.TAP)
                : new Gesture(front(pos, now.block, ENTRANCE_Y), Motion.TAKE);
    }

    private static Spot front(BlockPos pos, BlockState block, double up) {
        return Spots.side(pos, block.getValue(BeehiveBlock.FACING), up);
    }
}
