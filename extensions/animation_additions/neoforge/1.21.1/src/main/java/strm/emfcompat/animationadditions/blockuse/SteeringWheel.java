package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Aeronautics' bundled Simulated steering wheel: two fixed rim grips follow its rendered angle. */
final class SteeringWheel implements BlockTarget {
    private static final WheelAngle ANGLE = new WheelAngle("getRenderAngle"); // Already radians.
    public boolean matches(BlockState block) {
        return block.getBlock().getClass().getName().equals("dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlock");
    }
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return grip(player, pos, block, false);
    }
    public Spot supportHand(AbstractClientPlayer player, BlockPos pos, BlockState block) {
        return grip(player, pos, block, true);
    }
    private static Spot grip(AbstractClientPlayer player, BlockPos pos, BlockState block, boolean support) {
        Float angle = ANGLE.read(player.level(), pos);
        if (angle == null) return null;
        Direction facing = block.getValue(BlockStateProperties.HORIZONTAL_FACING);
        // Select at rest, never swap at the half-turn: that would teleport the grips between hands.
        Vec3 centre = WheelSpace.world(player.level(), pos, point(pos, block, 0, new Vector3f(.5f)));
        Vec3 side = WheelSpace.world(player.level(), pos, point(pos, block, 0, new Vector3f(1, .5f, .5f))).subtract(centre);
        Vec3 right = new Vec3(-player.getLookAngle().z, 0, player.getLookAngle().x);
        boolean positive = side.dot(right) >= 0;
        if (player.getMainArm() == HumanoidArm.LEFT) positive = !positive;
        if (support) positive = !positive;
        return WheelSpace.spot(player.level(), pos, point(pos, block, angle, new Vector3f(positive ? 1 : 0, .5f, .5f)),
                Vec3.atLowerCornerOf(facing.getNormal()));
    }
    public Vec3 swayCentre(Level level, BlockPos pos, BlockState block) {
        return WheelSpace.world(level, pos, point(pos, block, 0, new Vector3f(.5f)));
    }
    private static Vec3 point(BlockPos pos, BlockState block, float radians, Vector3f model) {
        Vector3f p = WheelGeometry.steering(model, block.getValue(BlockStateProperties.HORIZONTAL_FACING).getRotation(),
                block.getValue((BooleanProperty) block.getBlock().getStateDefinition().getProperty("on_floor")), radians);
        return new Vec3(pos.getX() + p.x, pos.getY() + p.y, pos.getZ() + p.z);
    }
    public boolean reachPose() { return true; }
    public boolean balancesReach() { return false; }
    public boolean quietsSwing() { return true; }
    public Gesture changed(BlockPos pos, Object before, Object now) { return null; }
}
