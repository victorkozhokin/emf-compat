package strm.touchnmotion.blockuse.create;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import strm.touchnmotion.interaction.EntityStates;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.touchnmotion.interaction.Body;
import strm.touchnmotion.blockuse.*;

/** All Create valve colours share this block class and the same octagonal rim geometry. */
public final class ValveHandle implements BlockTarget {
    private static final EntityStates<RimGrip> GRIPS = new EntityStates<>(RimGrip::new);
    private static final WheelAngle ANGLE = new WheelAngle("getIndependentAngle");
    private static final ModBlock BLOCK = ModBlock.exact("com.simibubi.create.content.kinetics.crank.ValveHandleBlock", "create:*_valve_handle");
    public boolean matches(BlockState block) {
        return BLOCK.is(block);
    }
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return grip(player, pos, block, false);
    }
    public Spot supportHand(AbstractClientPlayer player, BlockPos pos, BlockState block) {
        return grip(player, pos, block, true);
    }
    private static Spot grip(AbstractClientPlayer player, BlockPos pos, BlockState block, boolean support) {
        Float degrees = ANGLE.read(player.level(), pos);
        if (degrees == null) return null;
        Direction facing = block.getValue(BlockStateProperties.FACING);
        boolean positive = RimGrip.positiveSide(player, pos, point(pos, facing, 0, new Vector3f(.5f, 6.5f / 16f, .5f)),
                point(pos, facing, 0, new Vector3f(1, 6.5f / 16f, .5f)));
        long now = System.nanoTime();
        RimGrip state = GRIPS.seen(player.getUUID(), now).value;
        boolean fresh = state.left(pos, block, now) || positive != state.positive;
        if (fresh) state.lay(pos, block, positive, new SteeringGripMotion());
        state.turn((float) Math.toRadians(degrees), fresh, now);
        boolean rightHand = Body.right(player, !support);
        int hand = rightHand ? 0 : 1;
        positive = state.side(rightHand);
        Vec3 out = Vec3.atLowerCornerOf(facing.getNormal());
        return new Spot(point(pos, facing, (float) Math.toDegrees(state.motion.radians(hand)),
                new Vector3f(positive ? 14f / 16f : 2f / 16f, 6.5f / 16f, .5f))
                .add(out.scale(state.motion.lift(hand))), out);
    }

    public Float stanceAngle(Level level, BlockPos pos) { return ANGLE.read(level, pos); }
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
