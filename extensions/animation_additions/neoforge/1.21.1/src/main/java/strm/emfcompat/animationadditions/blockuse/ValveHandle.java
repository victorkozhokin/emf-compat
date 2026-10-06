package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.HumanoidArm;
import strm.emfcompat.animationadditions.interaction.SubLevels;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** All Create valve colours share this block class and the same octagonal rim geometry. */
final class ValveHandle implements BlockTarget {
    private static final strm.emfcompat.animationadditions.interaction.EntityStates<GripState> GRIPS =
            new strm.emfcompat.animationadditions.interaction.EntityStates<>(GripState::new);
    private static final class GripState {
        BlockPos pos;
        BlockState mount;
        long at;
        float frame = -1;
        boolean positive;
        SteeringGripMotion motion = new SteeringGripMotion();
    }
    private static final WheelAngle ANGLE = new WheelAngle("getIndependentAngle");
    public boolean matches(BlockState block) {
        return block.getBlock().getClass().getName().equals("com.simibubi.create.content.kinetics.crank.ValveHandleBlock");
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
        Vec3 side = SubLevels.at(player.level(), pos).directionToWorld(
                point(pos, facing, 0, new Vector3f(1, 6.5f / 16f, .5f))
                        .subtract(point(pos, facing, 0, new Vector3f(.5f, 6.5f / 16f, .5f))));
        Vec3 toward = SubLevels.toWorld(player.level(), pos,
                point(pos, facing, 0, new Vector3f(.5f, 6.5f / 16f, .5f))).subtract(player.position());
        boolean positive = WheelGeometry.positiveSide((float) side.x, (float) side.z, (float) toward.x, (float) toward.z);
        long now = System.nanoTime();
        GripState state = GRIPS.seen(player.getUUID(), now).value;
        boolean fresh = !pos.equals(state.pos) || !block.equals(state.mount)
                || positive != state.positive || now - state.at > 600_000_000L;
        if (fresh) {
            state.pos = pos.immutable();
            state.mount = block;
            state.positive = positive;
            state.motion = new SteeringGripMotion();
            state.frame = -1;
        }
        float frame = traben.entity_model_features.models.animation.state.EMFState.getFrameCounter();
        if (frame != state.frame) {
            state.motion.advance((float) Math.toRadians(degrees), fresh ? 0 : Math.min(.1f, (now - state.at) * 1e-9f));
            state.at = now;
            state.frame = frame;
        }
        boolean rightHand = (player.getMainArm() == HumanoidArm.RIGHT) != support;
        int hand = rightHand ? 0 : 1;
        positive = rightHand ? state.positive : !state.positive;
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
