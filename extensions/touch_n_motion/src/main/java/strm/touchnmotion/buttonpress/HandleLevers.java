package strm.touchnmotion.buttonpress;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import strm.touchnmotion.blockuse.ModBlock;
import strm.touchnmotion.blockuse.ModFailures;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Levers with a handle that turns with their signal: Create's analog lever (and Create: Connected's
 * linked one), Design n' Decor's stepped lever and breaker switch, and Compact Gearbox's controller,
 * whose stick does not move. Lever drugster lets each be held and dragged like Aeronautics' throttle;
 * without it they are clicked, and the hand is on the handle as it is drawn either way.
 *
 * <p>The handle is placed as each mod's renderer draws it: the handle model (with its own tilt),
 * turned about its hinge by the eased signal the block entity draws with ({@code clientState}), and
 * the whole turned to the block's face and facing as Create does it - or, for the controller, by its
 * blockstate. All of them optional: known by id and class name, nothing when the mod is not there.</p>
 */
public final class HandleLevers {
    private static final ModFailures FAILURES = new ModFailures("read a lever's handle");

    /** One kind of lever: the handle's grip at signal 0 and its hinge (model pixels), the turn for a signal, how the block is turned. */
    private record Kind(ModBlock block, Vector3f grip, Vector3f hinge, Function<Float, Float> degrees, boolean createFacing) {
    }

    private static final List<Kind> KINDS = List.of(
            // Design n' Decor first: its levers may build on Create's analog lever, with a handle of their own.
            new Kind(ModBlock.exact("dev.lopyluna.dndecor.content.blocks.stepped_lever.SteppedLeverBlock", "dndecor:stepped_lever"),
                    tilted(new Vector3f(8, 3.12132f, -5.12132f), new Vector3f(0, 1, 8), 45), new Vector3f(8, 4, 8),
                    v -> v / 15f * 135f - 22.5f, true),
            new Kind(ModBlock.exact("dev.lopyluna.dndecor.content.blocks.breaker_switch.BreakerSwitchBlock", "dndecor:breaker_switch"),
                    tilted(new Vector3f(8, 10.5f, 8), new Vector3f(8, .5f, 8), -45), new Vector3f(8, .5f, 8),
                    v -> v / 2f * (90f + 35f) - 35f / 2f, true),
            new Kind(ModBlock.family("com.simibubi.create.content.redstone.analogLever.AnalogLeverBlock",
                    "create:analog_lever", "create_connected:linked_analog_lever"),
                    tilted(new Vector3f(8, 1, -1), new Vector3f(8, 1, 8), 45), new Vector3f(8, 1, 8),
                    v -> v / 15f * 90f, true),
            new Kind(ModBlock.exact("com.boulterie.gearboxed.GearboxControllerBlock", "compactgearbox:gearbox_controller"),
                    new Vector3f(8, 10, 11.7f), new Vector3f(8, 10, 11.7f), v -> 0f, false));

    /** Half the width of a grip across the handle, blocks: where two hands would hold it. */
    private static final float HALF_GRIP = .08f;

    private HandleLevers() {
    }

    public static boolean is(BlockState block) {
        return kind(block) != null;
    }

    private static Kind kind(BlockState block) {
        for (Kind kind : KINDS) if (kind.block.is(block)) return kind;
        return null;
    }

    /** Where the handle's grip is drawn now, in the block's own space; {@code null} when it cannot be told. */
    public static Vec3 knob(Level level, BlockPos pos) {
        Vec3[] grips = grips(level, pos);
        return grips == null ? null : grips[0].add(grips[1]).scale(.5);
    }

    /** Two points across the handle's grip as drawn now, in the block's own space; {@code null} when it cannot be told. */
    public static Vec3[] grips(Level level, BlockPos pos) {
        BlockState block = level.getBlockState(pos);
        Kind kind = kind(block);
        if (kind == null || FAILURES.off()) return null;
        try {
            float value = kind.createFacing ? drawnValue(level.getBlockEntity(pos)) : 0f;
            Matrix4f m = new Matrix4f().translate(.5f, .5f, .5f);
            if (kind.createFacing) {
                AttachFace face = block.getValue(BlockStateProperties.ATTACH_FACE);
                Direction facing = block.getValue(BlockStateProperties.HORIZONTAL_FACING);
                // Create: turned about the vertical by the facing, then over onto a wall or the ceiling.
                float yaw = facing.toYRot() * (facing.getAxis() == Direction.Axis.X ? -1 : 1);
                m.rotateY((float) Math.toRadians(yaw)).rotateX((float) Math.toRadians(face == AttachFace.FLOOR ? 0 : face == AttachFace.WALL ? 90 : 180));
            } else {
                // A blockstate's y turns the model clockwise seen from above.
                Direction facing = facing(block);
                int y = switch (facing) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
                m.rotateY((float) Math.toRadians(-y));
            }
            m.translate(-.5f, -.5f, -.5f);
            Vector3f hinge = new Vector3f(kind.hinge).div(16);
            m.translate(hinge).rotateX((float) Math.toRadians(kind.degrees.apply(value))).translate(hinge.negate());
            Vector3f grip = new Vector3f(kind.grip).div(16);
            Vec3[] result = new Vec3[2];
            for (int i = 0; i < 2; i++) {
                Vector3f at = m.transformPosition(new Vector3f(grip).add(i == 0 ? -HALF_GRIP : HALF_GRIP, 0, 0));
                result[i] = new Vec3(pos.getX() + (double) at.x, pos.getY() + (double) at.y, pos.getZ() + (double) at.z);
            }
            return result;
        } catch (Throwable t) {
            FAILURES.failed(t);
            return null;
        }
    }

    /** The signal it gives, 0..15; {@code null} when it cannot be told or it has none. */
    public static Integer signal(Level level, BlockPos pos) {
        BlockState block = level.getBlockState(pos);
        Kind kind = kind(block);
        if (kind == null || !kind.createFacing) return null;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) return null;
        try {
            Field state = field(entity.getClass(), "state");
            return state == null ? null : state.getInt(entity);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** The eased signal the renderer draws the handle at this frame. */
    private static float drawnValue(BlockEntity entity) throws ReflectiveOperationException {
        if (entity == null) return 0f;
        Field eased = field(entity.getClass(), "clientState");
        if (eased != null) {
            Object lerped = eased.get(entity);
            if (lerped != null) {
                Method value = VALUE.computeIfAbsent(lerped.getClass(), c -> { try { return c.getMethod("getValue", float.class); } catch (NoSuchMethodException e) { return null; } });
                if (value != null) return (Float) value.invoke(lerped, strm.touchnmotion.platform.Platform.partialTick(false));
            }
        }
        Field state = field(entity.getClass(), "state");
        return state == null ? 0f : state.getInt(entity);
    }

    private static final Map<Class<?>, Method> VALUE = new HashMap<>();
    private static final Map<String, Field> FIELDS = new HashMap<>();

    private static Field field(Class<?> type, String name) {
        String key = type.getName() + "#" + name;
        if (FIELDS.containsKey(key)) return FIELDS.get(key);
        Field found = null;
        for (Class<?> c = type; c != null && found == null; c = c.getSuperclass()) {
            try {
                found = c.getDeclaredField(name);
                found.setAccessible(true);
            } catch (NoSuchFieldException | RuntimeException ignored) {
            }
        }
        FIELDS.put(key, found);
        return found;
    }

    /** The block's "facing", whatever property of its own the mod made it. */
    private static Direction facing(BlockState block) {
        for (var property : block.getProperties())
            if (property.getName().equals("facing") && block.getValue(property) instanceof Direction direction) return direction;
        return Direction.NORTH;
    }

    /** A point of a model element tilted {@code degrees} about x around {@code origin}, as the model file has it. */
    private static Vector3f tilted(Vector3f point, Vector3f origin, float degrees) {
        return new Vector3f(point).sub(origin).rotateX((float) Math.toRadians(degrees)).add(origin);
    }

    // ---- Lever drugster: our own player dragging one

    private static Method active;
    private static Field activePos;
    private static boolean absent;

    /** The lever our own player drags with Lever drugster now, or {@code null}. */
    public static BlockPos dragged() {
        if (absent) return null;
        try {
            if (active == null) {
                Class<?> client = Class.forName("prime.analogleverdrag.client.LeverDragClient");
                active = client.getMethod("isActive");
                activePos = client.getDeclaredField("activePos");
                activePos.setAccessible(true);
            }
            if (!(boolean) active.invoke(null)) return null;
            BlockPos pos = (BlockPos) activePos.get(null);
            var level = Minecraft.getInstance().level;
            return pos != null && level != null && is(level.getBlockState(pos)) ? pos : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            absent = true;
            return null;
        }
    }
}
