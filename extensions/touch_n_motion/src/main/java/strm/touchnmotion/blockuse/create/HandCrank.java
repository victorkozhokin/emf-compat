package strm.touchnmotion.blockuse.create;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import strm.touchnmotion.blockuse.*;

/**
 * Create's hand crank: looked at, the hand holds its grip and goes round with it as it turns, the
 * torso going with the hand a little ({@link #swayCentre}). Optional: found by name, nothing when
 * Create is not there. Only the hand crank itself - a valve handle is one too, drawn otherwise.
 *
 * <p>The grip is placed the way {@code HandCrankRenderer} draws the handle: the handle model
 * ({@code hand_crank/handle}, made facing north, the grip {@link #GRIP} off the axis) turned to the
 * crank's facing as {@code CachedBuffers.partialFacing} turns it for {@code facing.getOpposite()} -
 * about the middle, {@code rotateYDegrees(horizontalAngle)} then {@code rotateXDegrees(verticalAngle)}
 * - and then about the axis by {@code getIndependentAngle(partialTick)} degrees, as
 * {@code kineticRotationTransform} does.</p>
 */
public final class HandCrank implements BlockTarget {

    private static final ModBlock BLOCK = ModBlock.exact("com.simibubi.create.content.kinetics.crank.HandCrankBlock", "create:hand_crank");
    /** The middle of the grip in the handle's model, pixels; and the point on the axis level with it. */
    private static final Vector3f GRIP = new Vector3f(1f, 8f, 6f);
    private static final Vector3f AXIS = new Vector3f(8f, 8f, 6f);

    /** How the angle is read, by the block entity's class: the crank's and the valve handle's are two, and a method found on one cannot be called on the other. */
    private static final java.util.Map<Class<?>, Object[]> READERS = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Whether that class tells the angle in radians. Create up to 6.0.8 does - its renderer turns the
     * handle by the number as it is, a crank's being the sum of its speeds over 360, a valve's a real
     * angle; from 6.0.9 on both are degrees, turned into radians by the renderer. The older one is
     * known by the name its speed has there.
     */
    private static final java.util.Map<Class<?>, Boolean> RADIANS = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean has(Class<?> type, String field) {
        try {
            type.getField(field);
            return true;
        } catch (NoSuchFieldException none) {
            return false;
        }
    }

    private static final ModFailures FAILURES = new ModFailures("read the hand crank's angle");

    @Override
    public boolean matches(BlockState block) {
        return BLOCK.is(block);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        Float angle = angle(player.level(), pos);
        if (angle == null) return null;
        Direction facing = block.getValue(BlockStateProperties.FACING);
        return new Spot(place(pos, facing, angle, GRIP), Vec3.atLowerCornerOf(facing.getNormal()));
    }

    @Override
    public Vec3 swayCentre(Level level, BlockPos pos, BlockState block) {
        Direction facing = block.getValue(BlockStateProperties.FACING);
        return place(pos, facing, 0f, AXIS);
    }

    @Override
    public boolean reachPose() {
        return true;
    }

    @Override
    public boolean quietsSwing() {
        return true;
    }

    @Override
    public Gesture changed(BlockPos pos, Object before, Object now) {
        return null;
    }

    /** A point of the handle model, pixels, where the crank draws it with the handle turned {@code angle} degrees. */
    private static Vec3 place(BlockPos pos, Direction facing, float angle, Vector3f model) {
        Direction turned = facing.getOpposite();
        float horizontal = turned.getAxis().isVertical() ? 0f
 : turned.getAxis() == Direction.Axis.X ? -turned.toYRot() : turned.toYRot();
        float vertical = turned == Direction.UP ? -90f : turned == Direction.DOWN ? 90f : 0f;
        Vector3f v = new Vector3f(model).div(16f).sub(0.5f, 0.5f, 0.5f);
        new Quaternionf().rotationY((float) Math.toRadians(horizontal)).rotateX((float) Math.toRadians(vertical)).transform(v);
        Direction positive = Direction.get(Direction.AxisDirection.POSITIVE, facing.getAxis());
        Vector3f axis = new Vector3f(positive.getStepX(), positive.getStepY(), positive.getStepZ());
        new Quaternionf().rotationAxis((float) Math.toRadians(angle), axis).transform(v);
        return new Vec3(pos.getX() + 0.5 + v.x, pos.getY() + 0.5 + v.y, pos.getZ() + 0.5 + v.z);
    }

    @Override
    public Float stanceAngle(Level level, BlockPos pos) { return angle(level, pos); }

    /** The handle's angle as drawn this frame, degrees; {@code null} when it cannot be told. */
    static Float angle(Level level, BlockPos pos) {
        if (FAILURES.off()) return null;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) return null;
        try {
            float partial = strm.touchnmotion.platform.Platform.partialTick(false);
            Object[] reader = READERS.get(entity.getClass());
            if (reader == null) {
                try {
                    reader = new Object[]{entity.getClass().getMethod("getIndependentAngle", float.class)};
                } catch (NoSuchMethodException none) {
                    // Create Fly keeps the two numbers on the block entity and the sum of them in its renderer.
                    reader = new Object[]{entity.getClass().getField("independentAngle"), entity.getClass().getField("chasingAngularVelocity")};
                }
                READERS.put(entity.getClass(), reader);
                RADIANS.put(entity.getClass(), reader.length == 1 && has(entity.getClass(), "chasingVelocity"));
            }
            if (reader.length == 1) {
                float angle = (Float) ((Method) reader[0]).invoke(entity, partial);
                return RADIANS.get(entity.getClass()) ? (float) Math.toDegrees(angle) : angle;
            }
            return ((java.lang.reflect.Field) reader[0]).getFloat(entity) + partial * ((java.lang.reflect.Field) reader[1]).getFloat(entity);
        } catch (Throwable t) {
            FAILURES.failed(t);
            return null;
        }
    }
}
