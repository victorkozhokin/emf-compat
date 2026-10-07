package strm.emfcompat.animationadditions.blockuse;

import strm.emfcompat.animationadditions.interaction.EntityStates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import strm.emfcompat.animationadditions.interaction.Body;

/**
 * Create's train controls while a player drives with them: both hands are on the two levers and go
 * with them as they move, where the train is drawn now. The controls are part of a contraption,
 * not a block in the world, and are not looked at to be held - driving is the hold - so this is no
 * target found by the look: {@link BlockUse} asks {@link #held} first.
 *
 * <p>Who drives a contraption is every client's to see ({@code getControllingPlayer}); which of
 * its controls only the driver's own client is told ({@code ControlsHandler.getControlsPos}) - for
 * another player it is the controls nearest them. The levers are placed as {@code ControlsRenderer}
 * draws them, by the angles its {@code LeverAngles} hold, and carried into the world by
 * {@code AbstractContraptionEntity.toGlobalVector}. All by name: nothing without Create.</p>
 */
public final class TrainControls implements BlockTarget {

    static final TrainControls TARGET = new TrainControls();

    /** Both hands' grips, in the world. */
    record Grips(Spot main, Spot support) {
    }

    private static final String ENTITY = "com.simibubi.create.content.contraptions.AbstractContraptionEntity";
    private static final String BLOCK = "com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock";
    private static final String HANDLER = "com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler";
    /** The far end of a lever in its model ({@code controls/train/lever}: a bar x 4..6, y 11..13, z -1..9, turned 45 degrees about x at y 12, z 11), pixels. */
    private static final Vector3f LEVER_END = new Vector3f(5f, 12f, 0f);
    private static final Vector3f LEVER_ORIGIN = new Vector3f(0f, 12f, 11f);
    /** Looked for this far round the player, blocks, and this often, ticks. */
    private static final double RANGE = 6;
    private static final int EVERY = 10;

    private record Driven(WeakReference<Entity> contraption, BlockPos controls, int at) {
    }

    private static final Map<UUID, Driven> DRIVEN = EntityStates.alsoClear(new HashMap<>());
    private static final Map<Class<?>, Boolean> CONTRAPTIONS = new HashMap<>();
    private static Method controlling, toGlobal, getContraption, getBlocks, getActors, controlsPos, handlerContraption;
    private static Field temporaryData, localPos, speed, steering, equip;
    private static Method lerped;
    private static boolean looked;
    private static final ModFailures FAILURES = new ModFailures("read the train controls");

    private TrainControls() {
    }

    /** The levers of the controls {@code player} drives with now; {@code null} when they drive nothing. */
    static Grips held(AbstractClientPlayer player) {
        if (FAILURES.off()) return null;
        try {
            look();
            Driven driven = driven(player);
            if (driven == null) return null;
            Entity entity = driven.contraption.get();
            if (entity == null || entity.isRemoved()) return null;
            Object contraption = getContraption.invoke(entity);
            if (!(((Map<?, ?>) getBlocks.invoke(contraption)).get(driven.controls) instanceof StructureTemplate.StructureBlockInfo info)) return null;
            if (!info.state().getBlock().getClass().getName().equals(BLOCK)) return null;
            float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            float[] angles = angles(contraption, driven.controls, partial);
            Direction facing = info.state().getValue(BlockStateProperties.HORIZONTAL_FACING);
            Vec3 first = (Vec3) toGlobal.invoke(entity, lever(driven.controls, facing, true, angles), partial);
            Vec3 second = (Vec3) toGlobal.invoke(entity, lever(driven.controls, facing, false, angles), partial);
            // The lever on the player's right is the right hand's.
            Vec3 look = player.getLookAngle();
            Vec3 right = new Vec3(-look.z, 0, look.x);
            boolean firstIsRight = first.subtract(second).dot(right) >= 0;
            boolean mainIsFirst = Body.right(player, firstIsRight);
            return new Grips(new Spot(mainIsFirst ? first : second, Spots.UP), new Spot(mainIsFirst ? second : first, Spots.UP));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            FAILURES.failed(e);
            return null;
        }
    }

    /** Create's classes and methods, found once. */
    private static void look() throws ReflectiveOperationException {
        if (looked) return;
        looked = true;
        Class<?> entity = Class.forName(ENTITY);
        controlling = entity.getMethod("getControllingPlayer");
        toGlobal = entity.getMethod("toGlobalVector", Vec3.class, float.class);
        getContraption = entity.getMethod("getContraption");
        getBlocks = getContraption.getReturnType().getMethod("getBlocks");
        getActors = getContraption.getReturnType().getMethod("getActors");
        Class<?> handler = Class.forName(HANDLER);
        controlsPos = handler.getMethod("getControlsPos");
        handlerContraption = handler.getMethod("getContraption");
    }

    /** The contraption {@code player} drives, and which of its controls; {@code null} when they drive nothing. For telling other clients. */
    public static Entity drivenContraption(AbstractClientPlayer player) {
        Driven driven = drivenOrNull(player);
        return driven == null ? null : driven.contraption.get();
    }

    public static BlockPos drivenControls(AbstractClientPlayer player) {
        Driven driven = drivenOrNull(player);
        return driven == null ? null : driven.controls;
    }

    private static Driven drivenOrNull(AbstractClientPlayer player) {
        if (FAILURES.off()) return null;
        try {
            look();
            return driven(player);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            FAILURES.failed(e);
            return null;
        }
    }

    /** The contraption {@code player} drives and its controls; looked for now and then, kept between. */
    private static Driven driven(AbstractClientPlayer player) throws ReflectiveOperationException {
        Driven known = DRIVEN.get(player.getUUID());
        if (known != null && Math.abs(player.tickCount - known.at) < EVERY) return known.contraption == null ? null : known;
        Driven found = find(player);
        DRIVEN.put(player.getUUID(), found != null ? found : new Driven(null, null, player.tickCount));
        return found;
    }

    private static Driven find(AbstractClientPlayer player) throws ReflectiveOperationException {
        if (player == Minecraft.getInstance().player) {
            // Our own: Create's client knows both.
            Object entity = handlerContraption.invoke(null);
            Object pos = controlsPos.invoke(null);
            return entity instanceof Entity e && pos instanceof BlockPos p ? new Driven(new WeakReference<>(e), p, player.tickCount) : null;
        }
        // Another player's own game says which, when the server passes it on; else it is the nearest controls of what they drive.
        if (strm.emfcompat.animationadditions.net.Inputs.told(player)) {
            BlockPos told = strm.emfcompat.animationadditions.net.Inputs.drivePos(player);
            Entity train = told == null ? null : player.level().getEntity(strm.emfcompat.animationadditions.net.Inputs.driveEntity(player));
            return train == null ? null : new Driven(new WeakReference<>(train), told, player.tickCount);
        }
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(RANGE),
                e -> CONTRAPTIONS.computeIfAbsent(e.getClass(), TrainControls::isContraption))) {
            if (!(controlling.invoke(entity) instanceof Optional<?> by) || !by.isPresent() || !by.get().equals(player.getUUID())) continue;
            // Which controls is not sent: the nearest to the player.
            BlockPos nearest = null;
            double distance = Double.MAX_VALUE;
            Map<?, ?> blocks = (Map<?, ?>) getBlocks.invoke(getContraption.invoke(entity));
            for (Map.Entry<?, ?> block : blocks.entrySet()) {
                if (!(block.getValue() instanceof StructureTemplate.StructureBlockInfo info)
                        || !info.state().getBlock().getClass().getName().equals(BLOCK)) continue;
                BlockPos pos = (BlockPos) block.getKey();
                double d = ((Vec3) toGlobal.invoke(entity, Vec3.atCenterOf(pos), 1f)).distanceToSqr(player.position());
                if (d < distance) {
                    distance = d;
                    nearest = pos;
                }
            }
            if (nearest != null) return new Driven(new WeakReference<>(entity), nearest, player.tickCount);
        }
        return null;
    }

    private static boolean isContraption(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) if (c.getName().equals(ENTITY)) return true;
        return false;
    }

    /** {speed, steering, equip} of the controls' levers ({@code ControlsMovementBehaviour.LeverAngles}); at rest when they cannot be read. */
    private static float[] angles(Object contraption, BlockPos controls, float partial) {
        float[] out = {0f, 0f, 1f};
        try {
            for (Object actor : (List<?>) getActors.invoke(contraption)) {
                Object context = ((Pair<?, ?>) actor).getRight();
                if (temporaryData == null) {
                    temporaryData = context.getClass().getField("temporaryData");
                    localPos = context.getClass().getField("localPos");
                }
                if (!controls.equals(localPos.get(context))) continue;
                Object data = temporaryData.get(context);
                if (data == null || !data.getClass().getName().endsWith("LeverAngles")) return out;
                if (speed == null) {
                    speed = accessible(data.getClass().getDeclaredField("speed"));
                    steering = accessible(data.getClass().getDeclaredField("steering"));
                    equip = accessible(data.getClass().getDeclaredField("equipAnimation"));
                    lerped = speed.getType().getMethod("getValue", float.class);
                }
                out[0] = (float) lerped.invoke(speed.get(data), partial);
                out[1] = (float) lerped.invoke(steering.get(data), partial);
                out[2] = (float) lerped.invoke(equip.get(data), partial);
                return out;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
        return out;
    }

    private static Field accessible(Field field) {
        field.setAccessible(true);
        return field;
    }

    /**
     * The end of a lever, in the contraption's own space, as {@code ControlsRenderer.render} draws
     * it: about the block's middle, turned to the facing, the lever swung about its pivot by its
     * angle, raised as the controls open.
     */
    private static Vec3 lever(BlockPos pos, Direction facing, boolean first, float[] angles) {
        float vertical = Math.max(-45f, Math.min(45f, first ? angles[0] * 70f - 25f : angles[1] * 15f));
        float horizontal = 180f + (facing.getAxis() == Direction.Axis.X ? -facing.toYRot() : facing.toYRot());
        float raised = -0.15f + (0.05f + 0.15f) * angles[2];
        // The model's own turn of the bar.
        Vector3f p = new Vector3f(LEVER_END).sub(LEVER_ORIGIN);
        new Quaternionf().rotationX((float) Math.toRadians(45)).transform(p);
        p.add(LEVER_ORIGIN).div(16f);
        p.add(first ? 0f : 0.375f, -0.375f, -0.1875f).sub(0.5f, 0.5f, 0.5f);
        new Quaternionf().rotationX((float) Math.toRadians(45)).transform(p);
        p.add(0f, raised, 0f);
        new Quaternionf().rotationX((float) Math.toRadians(vertical - 45f)).transform(p);
        p.add(0f, 0.25f, 0.25f);
        new Quaternionf().rotationY((float) Math.toRadians(horizontal)).transform(p);
        return new Vec3(pos.getX() + 0.5 + p.x, pos.getY() + 0.5 + p.y, pos.getZ() + 0.5 + p.z);
    }

    // Not a target of the look: BlockUse takes the grips from held().

    @Override
    public boolean matches(BlockState block) {
        return false;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return null;
    }

    @Override
    public boolean quietsSwing() {
        return true;
    }

    @Override
    public Gesture changed(BlockPos pos, Object before, Object now) {
        return null;
    }
}
