package strm.emfcompat.animationadditions.blockuse.aeronautics;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.Minecraft;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import java.lang.reflect.Method;
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
import strm.emfcompat.animationadditions.interaction.SubLevels;
import strm.emfcompat.animationadditions.interaction.Body;
import strm.emfcompat.animationadditions.DebugLog;
import strm.emfcompat.animationadditions.blockuse.*;

/** Aeronautics' bundled Simulated steering wheel: six rim anchors and alternating regrips follow its rendered angle. */
public final class SteeringWheel implements BlockTarget {
    private static final WheelAngle ANGLE = new WheelAngle("getRenderAngle"); // Already radians.
    private static final EntityStates<GripState> GRIPS = new EntityStates<>(GripState::new);
    private static Object handler;
    private static Method held, activeBlock;
    private static boolean holdFailed;
    private static final class GripState extends RimGrip {
        HumanoidArm main;
        final DebugLog.Pace tracePace = new DebugLog.Pace();
    }
    private static final ModBlock BLOCK = ModBlock.exact("dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlock", "simulated:steering_wheel");
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
        Float angle = ANGLE.read(player.level(), pos);
        if (angle == null) return null;
        Direction facing = block.getValue(BlockStateProperties.HORIZONTAL_FACING);
        long now = System.nanoTime();
        GripState state = GRIPS.seen(player.getUUID(), now).value;
        boolean fresh = state.left(pos, block, now) || player.getMainArm() != state.main;
        if (fresh) {
            Vec3 centre = point(pos, block, 0, new Vector3f(.5f));
            boolean positiveSide = RimGrip.positiveSide(player, pos, centre, point(pos, block, 0, new Vector3f(1, .5f, .5f)));
            state.main = player.getMainArm();
            Vec3 worldCentre = SubLevels.at(player.level(), pos).toWorld(centre);
            float upper = 0;
            if (worldCentre.y - player.getY() < .75) {
                Vec3 tangent = SubLevels.at(player.level(), pos).directionToWorld(
                        point(pos, block, (float)(Math.PI / 2), new Vector3f(positiveSide ? 1 : 0, .5f, .5f)).subtract(centre));
                if (Math.abs(tangent.y) > .01) upper = tangent.y > 0 ? 45 : -45;
            }
            state.lay(pos, block, positiveSide, new SteeringGripMotion(upper));
        }
        state.turn(angle, fresh, now);
        boolean rightHand = Body.right(player, !support);
        int hand = rightHand ? 0 : 1;
        boolean positive = state.side(rightHand);
        Vec3 out = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 grip = point(pos, block, state.motion.radians(hand), new Vector3f(positive ? 1 : 0, .5f, .5f))
                .add(out.scale(state.motion.lift(hand)));
        if (!support && DebugLog.trace()
                && state.tracePace.due(100_000_000L)) {
            org.slf4j.LoggerFactory.getLogger("EMFCompatBlockUse").info(
                    "[RegripTrace] moving={} transfers={} rightPhase={} leftPhase={} rightLift={} leftLift={} rightSlot={} leftSlot={}",
                    state.motion.moving(), state.motion.transfers, state.motion.radians(0), state.motion.radians(1),
                    state.motion.lift(0), state.motion.lift(1), state.motion.slot(0), state.motion.slot(1));
        }
        return new Spot(grip, out);
    }

    public boolean holds(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        // Another player's hold is their own game's to say.
        if (player != Minecraft.getInstance().player) return strm.emfcompat.animationadditions.net.Inputs.holds(player, pos);
        if (holdFailed) return false;
        try {
            if (handler == null) {
                handler = Class.forName("dev.simulated_team.simulated.index.SimClickInteractions")
                        .getField("STEERING_WHEEL_MANAGER").get(null);
                Class<?> manager = Class.forName("dev.simulated_team.simulated.util.hold_interaction.HoldInteractionManager");
                Class<?> interaction = Class.forName("dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction");
                held = manager.getMethod("isActive", interaction);
                activeBlock = handler.getClass().getMethod("isBlockActive", BlockPos.class);
            }
            return (boolean) held.invoke(null, handler) && (boolean) activeBlock.invoke(handler, pos);
        } catch (ReflectiveOperationException | ClassCastException e) {
            holdFailed = true;
            org.slf4j.LoggerFactory.getLogger("EMFCompatBlockUse").warn("[BlockUse] cannot read held steering wheel", e);
            return false;
        }
    }
    public Float stanceAngle(Level level, BlockPos pos) {
        Float radians = ANGLE.read(level, pos);
        return radians == null ? null : (float) Math.toDegrees(radians);
    }
    public Vec3 swayCentre(Level level, BlockPos pos, BlockState block) {
        return point(pos, block, 0, new Vector3f(.5f));
    }
    private static Vec3 point(BlockPos pos, BlockState block, float radians, Vector3f model) {
        Vector3f p = WheelGeometry.steering(model, block.getValue(BlockStateProperties.HORIZONTAL_FACING).getRotation(),
                block.getValue((BooleanProperty) block.getBlock().getStateDefinition().getProperty("on_floor")), radians);
        // In double: a sub-level's plot is millions of blocks out, where a float is off by whole blocks.
        return new Vec3(pos.getX() + (double) p.x, pos.getY() + (double) p.y, pos.getZ() + (double) p.z);
    }
    public boolean reachPose() { return true; }
    public boolean balancesReach() { return false; }
    public boolean quietsSwing() { return true; }
    public Gesture changed(BlockPos pos, Object before, Object now) { return null; }
}
