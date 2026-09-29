package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Points on blocks for {@link BlockTarget}s. */
final class Spots {

    static final Vec3 UP = new Vec3(0, 1, 0);

    private Spots() {
    }

    /** A point of the block, pixels, on its top side: the hand comes down onto it. */
    static BlockTarget.Spot top(BlockPos pos, double x, double y, double z) {
        return new BlockTarget.Spot(new Vec3(pos.getX() + x / 16, pos.getY() + y / 16, pos.getZ() + z / 16), UP);
    }

    /**
     * A point of a model made facing south, pixels, turned as the blockstate turns it for
     * {@code facing} (south 0, west 90, north 180, east 270 degrees clockwise from above), on its
     * top side.
     */
    static BlockTarget.Spot turned(BlockPos pos, Direction facing, double x, double y, double z) {
        double dx = x - 8, dz = z - 8;
        double rx, rz;
        switch (facing) {
            case WEST -> { rx = -dz; rz = dx; }
            case NORTH -> { rx = -dx; rz = -dz; }
            case EAST -> { rx = dz; rz = -dx; }
            default -> { rx = dx; rz = dz; }
        }
        return top(pos, 8 + rx, y, 8 + rz);
    }
}
