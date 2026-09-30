package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** All Create valve colours share this block class and the same octagonal rim geometry. */
final class ValveHandle implements BlockTarget {
    private static final WheelAngle ANGLE = new WheelAngle("getIndependentAngle");
    public boolean matches(BlockState block) {
        return block.getBlock().getClass().getName().equals("com.simibubi.create.content.kinetics.crank.ValveHandleBlock");
    }
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        Float degrees = ANGLE.read(player.level(), pos);
        if (degrees == null) return null;
        Direction facing = block.getValue(BlockStateProperties.FACING);
        return new Spot(point(pos, facing, degrees, new Vector3f(2f / 16f, 6.5f / 16f, .5f)), Vec3.atLowerCornerOf(facing.getNormal()));
    }
    public Vec3 swayCentre(Level level, BlockPos pos, BlockState block) {
        return point(pos, block.getValue(BlockStateProperties.FACING), 0, new Vector3f(.5f, 6.5f / 16f, .5f));
    }
    private static Vec3 point(BlockPos pos, Direction facing, float degrees, Vector3f model) {
        Vector3f p = WheelGeometry.valve(model, new Vector3f(facing.getStepX(), facing.getStepY(), facing.getStepZ()),
                (float) Math.toRadians(degrees));
        return new Vec3(pos.getX() + (double) p.x, pos.getY() + (double) p.y, pos.getZ() + (double) p.z);
    }
    public boolean reachPose() { return true; }
    public boolean balancesReach() { return false; }
    public boolean quietsSwing() { return true; }
    public Gesture changed(BlockPos pos, Object before, Object now) { return null; }
}
