package strm.touchnmotion.blockuse.create;

import strm.touchnmotion.interaction.EntityStates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import strm.touchnmotion.blockuse.*;

/**
 * The value boxes of Create's blocks - the small panels on a block a value is set on, a filter
 * item is put into, a redstone link's two frequency items: a speed controller's speed, a funnel's
 * or an observer's filter, a creative motor, a bearing's mode, and any other block of Create or of
 * a mod made on it, with no list of them. Looked at where a click would use the box
 * ({@code ValueSettingsInputHandler}'s own test), the hand goes to it; its value set, the hand taps
 * it; an item put into it, the hand puts; taken out, it takes.
 *
 * <p>Optional and by name all through: a block entity that is a {@code SmartBlockEntity}, its
 * behaviours that are a {@code ValueSettingsBehaviour} (their {@code getSlotPositioning()} is where
 * the box is, {@code getValueSettings()} and {@code getFilter()} what it holds) or a
 * {@code LinkBehaviour}. All of that is every client's to see, so this is any player's. After
 * the targets of single blocks: a block with a gesture of its own keeps it, and has its boxes when
 * that gesture has nowhere for the hand to go.</p>
 */
public final class ValuePanel implements BlockTarget {

    private static final String SMART = "com.simibubi.create.foundation.blockEntity.SmartBlockEntity";
    private static final String SETTINGS = "com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour";
    private static final String SIDED_FILTER = "com.simibubi.create.foundation.blockEntity.behaviour.filtering.SidedFilteringBehaviour";
    private static final String LINK = "com.simibubi.create.content.redstone.link.LinkBehaviour";
    private static final ResourceLocation WRENCH = ResourceLocation.fromNamespaceAndPath("create", "wrench");

    /** Which of our kinds a class is, found once. */
    private static final Map<Class<?>, Boolean> SMART_ENTITIES = new HashMap<>();
    private static final Map<Class<?>, Kind> KINDS = new HashMap<>();
    private static final Map<String, Optional<AccessibleObject>> MEMBERS = new HashMap<>();
    private static boolean warned;

    private enum Kind {NONE, SETTINGS, LINK}

    /** A box: what it holds, as text; whether that is an item put into it; where it is. */
    private record Box(String holds, boolean item, boolean empty, Spot spot) {
    }

    /** Every box as it is now, and where the box the player looks at is - the one a use is on. Equal by what the boxes hold. */
    private record Seen(BlockState block, List<Box> boxes, Spot looked) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Seen s && s.block == block && holds().equals(s.holds());
        }

        @Override
        public int hashCode() {
            return holds().hashCode();
        }

        private List<String> holds() {
            return boxes.stream().map(Box::holds).toList();
        }
    }

    /** The box each player looks at, for the snapshot: {@link #changed} is not told where the look is. */
    private final Map<UUID, Box> looked = EntityStates.alsoClear(new HashMap<>());

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof EntityBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        Box box = null;
        for (Box each : boxes(player, player.level(), pos, block, hit)) {
            if (each.spot != null) box = each;
        }
        if (box == null) looked.remove(player.getUUID());
        else looked.put(player.getUUID(), box);
        return box == null ? null : box.spot;
    }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        Box at = looked.get(player.getUUID());
        return new Seen(block, boxes(player, level, pos, block, null), at == null ? null : at.spot);
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now) || before.boxes.size() != now.boxes.size()) return null;
        Spot at = now.looked != null ? now.looked : before.looked;
        if (at == null) return null;
        for (int i = 0; i < now.boxes.size(); i++) {
            Box box = now.boxes.get(i);
            if (box.holds.equals(before.boxes.get(i).holds)) continue;
            // An item come is put in, gone is taken out; a value set is a tap.
            return new Gesture(at, !box.item ? Motion.TAP : box.empty ? Motion.TAKE : Motion.PUT);
        }
        return null;
    }

    /**
     * The block's boxes, in the behaviours' order; with {@code hit}, each one's spot when the look
     * is on it and a click would use it, {@code null} otherwise.
     */
    private static List<Box> boxes(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block, BlockHitResult hit) {
        List<Box> out = new ArrayList<>();
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null || !SMART_ENTITIES.computeIfAbsent(entity.getClass(), c -> is(c, SMART))) return out;
        if (!(call(entity, "getAllBehaviours") instanceof Collection<?> behaviours)) return out;
        if (hit == null && player == Minecraft.getInstance().player && Minecraft.getInstance().hitResult instanceof BlockHitResult own
                && own.getType() == HitResult.Type.BLOCK && own.getBlockPos().equals(pos)) {
            hit = own;
        }
        for (Object behaviour : behaviours) {
            switch (KINDS.computeIfAbsent(behaviour.getClass(), ValuePanel::kind)) {
                case SETTINGS -> settings(out, player, level, pos, block, hit, behaviour);
                case LINK -> {
                    link(out, level, pos, block, hit, behaviour, true);
                    link(out, level, pos, block, hit, behaviour, false);
                }
                default -> {
                }
            }
        }
        return out;
    }

    private static void settings(List<Box> out, AbstractClientPlayer player, Level level, BlockPos pos, BlockState block,
                                 BlockHitResult hit, Object behaviour) {
        Direction side = hit == null ? null : hit.getDirection();
        // A filter with a slot a side: the side looked at.
        if (side != null && is(behaviour.getClass(), SIDED_FILTER)) {
            Object sided = call(behaviour, "get", Direction.class, side);
            if (sided != null) behaviour = sided;
        }
        Object filter = call(behaviour, "getFilter");
        boolean item = filter instanceof ItemStack;
        boolean empty = item && ((ItemStack) filter).isEmpty();
        String holds = call(behaviour, "getValueSettings") + (item ? " " + name((ItemStack) filter) : "");
        Spot spot = null;
        if (hit != null && Boolean.TRUE.equals(call(behaviour, "isActive")) && wrenchOk(player, behaviour)) {
            Object slot = call(behaviour, "getSlotPositioning");
            if (slot != null && onSide(slot, block, side)
                    && Boolean.TRUE.equals(call(behaviour, "testHit", Vec3.class, hit.getLocation()))) {
                spot = spot(slot, level, pos, block, side);
            }
        }
        out.add(new Box(holds, item, empty, spot));
    }

    /** One of a redstone link's two frequency slots. */
    private static void link(List<Box> out, Level level, BlockPos pos, BlockState block, BlockHitResult hit, Object behaviour, boolean first) {
        Object key = call(behaviour, "getNetworkKey");
        Object frequency = key == null ? null : call(key, first ? "getFirst" : "getSecond");
        ItemStack stack = frequency != null && call(frequency, "getStack") instanceof ItemStack s ? s : ItemStack.EMPTY;
        Spot spot = null;
        if (hit != null && Boolean.TRUE.equals(call(behaviour, "testHit", Boolean.class, first, Vec3.class, hit.getLocation()))) {
            Object slot = field(behaviour, first ? "firstSlot" : "secondSlot");
            if (slot != null) spot = spot(slot, level, pos, block, hit.getDirection());
        }
        out.add(new Box(name(stack), true, stack.isEmpty(), spot));
    }

    /** A box only shown with a wrench is only used with one. */
    private static boolean wrenchOk(AbstractClientPlayer player, Object behaviour) {
        if (!Boolean.TRUE.equals(call(behaviour, "onlyVisibleWithWrench"))) return true;
        return BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).equals(WRENCH);
    }

    /** A box that is on whichever side is looked at ({@code ValueBoxTransform.Sided}) is put on that side, if it has one there. */
    private static boolean onSide(Object slot, BlockState block, Direction side) {
        if (member(slot.getClass(), "fromSide", Direction.class).isEmpty()) return true;
        if (!Boolean.TRUE.equals(call(slot, "isSideActive", BlockState.class, block, Direction.class, side))) return false;
        call(slot, "fromSide", Direction.class, side);
        return true;
    }

    private static Spot spot(Object slot, Level level, BlockPos pos, BlockState block, Direction side) {
        Object offset = call(slot, "getLocalOffset", LevelAccessor.class, level, BlockPos.class, pos, BlockState.class, block);
        if (!(offset instanceof Vec3 local)) return null;
        return new Spot(new Vec3(pos.getX() + local.x, pos.getY() + local.y, pos.getZ() + local.z), Vec3.atLowerCornerOf(side.getNormal()));
    }

    private static String name(ItemStack stack) {
        return stack.isEmpty() ? "" : stack.getItem() + "x" + stack.getCount();
    }

    private static Kind kind(Class<?> type) {
        return is(type, LINK) ? Kind.LINK : is(type, SETTINGS) ? Kind.SETTINGS : Kind.NONE;
    }

    private static boolean is(Class<?> type, String name) {
        return ModAccess.is(type, name);
    }

    /** Calls {@code name(types...)}: {@code spec} is the argument types and values, in pairs. {@code null} when it cannot be. */
    private static Object call(Object target, String name, Object... spec) {
        Class<?>[] types = new Class<?>[spec.length / 2];
        Object[] args = new Object[spec.length / 2];
        for (int i = 0; i < types.length; i++) {
            types[i] = (Class<?>) spec[2 * i];
            args[i] = spec[2 * i + 1];
        }
        Optional<AccessibleObject> member = member(target.getClass(), name, types);
        if (member.isEmpty()) return null;
        try {
            return ((Method) member.get()).invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(name, target, e);
            return null;
        }
    }

    private static Object field(Object target, String name) {
        String key = target.getClass().getName() + "." + name;
        Optional<AccessibleObject> member = MEMBERS.computeIfAbsent(key, k -> {
            for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    Field field = c.getDeclaredField(name);
                    field.setAccessible(true);
                    return Optional.of(field);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                }
            }
            return Optional.empty();
        });
        try {
            return member.isEmpty() ? null : ((Field) member.get()).get(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(name, target, e);
            return null;
        }
    }

    /**
     * The method, public or not, of the class or one it extends; found once. A class that names
     * another mod's types in its methods (a ComputerCraft peripheral) cannot be looked through
     * without that mod - a {@code LinkageError}: the class it extends is tried next.
     */
    private static Optional<AccessibleObject> member(Class<?> type, String name, Class<?>... types) {
        String key = type.getName() + "#" + name + "/" + types.length;
        return MEMBERS.computeIfAbsent(key, k -> {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                try {
                    Method method = c.getDeclaredMethod(name, types);
                    method.setAccessible(true);
                    return Optional.of(method);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                }
            }
            // A default method of an interface.
            try {
                Method method = type.getMethod(name, types);
                method.setAccessible(true);
                return Optional.of(method);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return Optional.empty();
            }
        });
    }

    private static void warn(String name, Object target, Exception e) {
        if (warned) return;
        warned = true;
        LoggerFactory.getLogger("EMFCompatBlockUse").warn("[BlockUse] cannot read {} on {}", name, target.getClass().getName(), e);
    }
}
