package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A chiseled bookshelf: the hand goes to the slot looked at - one with a book in it, or an empty
 * one with a book in the hand - and puts a book in or takes one out of the slot that changed.
 *
 * <p>Six slots on the front, three across and two up. Which one a point on the front is follows
 * {@code ChiseledBookShelfBlock.getHitSlot}: across, from the front's own left as the game counts
 * it, split at 6/16 and 11/16; the top row first.</p>
 */
final class ChiseledShelf implements BlockTarget {

    /** The middle of each column across the front, and of each row up it, blocks. */
    private static final double[] COLUMNS = {3 / 16.0, 8.5 / 16, 13.5 / 16};
    private static final double[] ROWS = {0.75, 0.25};

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof ChiseledBookShelfBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        Direction facing = block.getValue(HorizontalDirectionalBlock.FACING);
        if (hit.getDirection() != facing) return null;
        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        double across = switch (facing) {
            case NORTH -> 1 - local.x;
            case SOUTH -> local.x;
            case WEST -> local.z;
            case EAST -> 1 - local.z;
            default -> 0.5;
        };
        int column = across < 0.375 ? 0 : across < 0.6875 ? 1 : 2;
        int row = local.y >= 0.5 ? 0 : 1;
        int slot = column + row * 3;
        boolean occupied = block.getValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(slot));
        boolean book = player.getMainHandItem().is(ItemTags.BOOKSHELF_BOOKS);
        return occupied || book ? spot(pos, facing, slot) : null;
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now)) return null;
        if (!matches(before) || !matches(now)) return null;
        for (int slot = 0; slot < ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.size(); slot++) {
            BooleanProperty property = ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(slot);
            if (before.getValue(property) != now.getValue(property)) {
                Motion motion = now.getValue(property) ? Motion.PUT : Motion.TAKE;
                return new Gesture(spot(pos, now.getValue(HorizontalDirectionalBlock.FACING), slot), motion);
            }
        }
        return null;
    }

    /** The middle of a slot on the front. */
    private static Spot spot(BlockPos pos, Direction facing, int slot) {
        double across = COLUMNS[slot % 3];
        double y = pos.getY() + ROWS[slot / 3];
        Vec3 point = switch (facing) {
            case NORTH -> new Vec3(pos.getX() + 1 - across, y, pos.getZ());
            case SOUTH -> new Vec3(pos.getX() + across, y, pos.getZ() + 1);
            case WEST -> new Vec3(pos.getX(), y, pos.getZ() + across);
            default -> new Vec3(pos.getX() + 1, y, pos.getZ() + 1 - across);
        };
        return new Spot(point, Vec3.atLowerCornerOf(facing.getNormal()));
    }
}
