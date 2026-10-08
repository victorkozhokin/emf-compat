package strm.touchnmotion.blockuse.vanilla;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.touchnmotion.blockuse.*;

/**
 * A campfire (a soul one too): with something that cooks on it in the hand, the hand goes to the
 * free place on the grill the food will go to - the first empty one, as {@code placeFood} fills
 * them - and puts it down there. Food that is done falls off by itself: that is not the hand's.
 *
 * <p>Using it changes nothing in the block's state: what is watched is which of the four places
 * hold something.</p>
 */
public final class Campfire implements BlockTarget {

    /** The places on the grill: this high, blocks, and this far from the middle along both axes, as {@code CampfireRenderer} draws them. */
    private static final double GRILL_Y = 0.44921875;
    private static final float CORNER = 0.3125f;

    /** What is on the grill, one bit per place, with the block as it is. */
    private record Seen(BlockState block, int filled) {
    }

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof CampfireBlock;
    }

    @Override
    public Object snapshot(Level level, BlockPos pos, BlockState block) {
        int filled = 0;
        if (level.getBlockEntity(pos) instanceof CampfireBlockEntity campfire) {
            for (int i = 0; i < campfire.getItems().size(); i++) {
                if (!campfire.getItems().get(i).isEmpty()) filled |= 1 << i;
            }
        }
        return new Seen(block, filled);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        if (!(player.level().getBlockEntity(pos) instanceof CampfireBlockEntity campfire)) return null;
        if (!strm.touchnmotion.platform.Platform.cooksOnCampfire(campfire, player.getMainHandItem())) return null;
        for (int i = 0; i < campfire.getItems().size(); i++) {
            if (campfire.getItems().get(i).isEmpty()) return place(pos, block, i);
        }
        return null;
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now)) return null;
        // Only a place filled: the one the hand put the food on.
        int added = now.filled & ~before.filled;
        if (added == 0) return null;
        return new Gesture(place(pos, now.block, Integer.numberOfTrailingZeros(added)), Motion.PUT);
    }

    /**
     * Place {@code slot} on the grill: {@code CampfireRenderer} turns each by its own direction,
     * counted on from the campfire's facing, and draws it a corner's way off the middle.
     */
    private static Spot place(BlockPos pos, BlockState block, int slot) {
        Direction facing = block.getValue(CampfireBlock.FACING);
        Direction direction = Direction.from2DDataValue((slot + facing.get2DDataValue()) % 4);
        // The renderer's -toYRot about y, then 90 about x, then (-corner, -corner, 0): the last two
        // together put it at (-corner, 0, -corner) before the turn about y.
        Vector3f off = new Quaternionf().rotationY((float) Math.toRadians(-direction.toYRot()))
                .transform(new Vector3f(-CORNER, 0f, -CORNER));
        Vec3 point = new Vec3(pos.getX() + 0.5 + off.x, pos.getY() + GRILL_Y, pos.getZ() + 0.5 + off.z);
        return new Spot(point, new Vec3(0, 1, 0));
    }
}
