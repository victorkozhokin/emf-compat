package strm.emfcompat.animationadditions.blockuse.supplementaries;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import strm.emfcompat.animationadditions.blockuse.*;

/**
 * Supplementaries' safe: looked at on its door, the hand is on the door; opened ({@code open}), it
 * pulls the door. Optional, by name. The safe is a pixel in from the block's sides.
 */
public final class Safe implements BlockTarget {

    private static final ModBlock BLOCK = ModBlock.exact("net.mehvahdjukaar.supplementaries.common.block.blocks.SafeBlock", "supplementaries:safe");
    private static final double DOOR = 7 / 16.0;

    @Override
    public boolean matches(BlockState block) {
        return BLOCK.is(block);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return hit.getDirection() == facing(block) ? door(pos, block) : null;
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now) || !matches(before) || !matches(now)) return null;
        return !open(before) && open(now) ? new Gesture(door(pos, now), Motion.TAKE) : null;
    }

    private static boolean open(BlockState block) {
        Property<?> open = block.getBlock().getStateDefinition().getProperty("open");
        return open != null && Boolean.TRUE.equals(block.getValue(open));
    }

    private static Direction facing(BlockState block) {
        Property<?> facing = block.getBlock().getStateDefinition().getProperty("facing");
        return facing != null && block.getValue(facing) instanceof Direction d ? d : Direction.NORTH;
    }

    private static Spot door(BlockPos pos, BlockState block) {
        Vec3 out = Vec3.atLowerCornerOf(facing(block).getNormal());
        return new Spot(Vec3.atCenterOf(pos).add(out.scale(DOOR)), out);
    }
}
