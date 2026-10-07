package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import strm.emfcompat.animationadditions.interaction.SubLevels;
import strm.emfcompat.animationadditions.interaction.Body;

/**
 * A block of a mod's that items are put on or into by hand and taken back - Create's depot,
 * mechanical crafter, deployer, packager, frogport; Simulated's navigation table; Aeronautics'
 * mounted potato cannon; Supplementaries' item shelf, pedestal, jar, hourglass, notice board,
 * flower box: with something in the hand, or something in the block, the hand goes to
 * where the item goes - the top, or the side the block faces (on a table that faces up both
 * hands wait on its edges); the item put there, the hand puts
 * it; taken, it takes it. Optional: the block and what it holds are found by name (a method or a
 * field giving an {@code ItemStack} or an item handler; with none named, the block entity itself,
 * a container), and what it holds is every client's to
 * see - this is any player's.
 *
 * <p>A belt, a funnel, the block's own work change what it holds too: it is a hand's doing only
 * when the item that came is the one the player held, or when it went with the hand empty. A use
 * that shows in nothing the block holds is still a tap: the arm swings.</p>
 */
public final class ItemRest implements BlockTarget {

    private final String blockClass;
    private final ModAccess held;
    /** The top the item lies on, pixels; negative for the side the block faces. */
    private final double top;
    /** On the side the block faces: how far in from that side the item is, pixels. */
    private final double inset;
    /** The top the item lies on when the block is on its side (faces along the ground), pixels; negative when it is the same. */
    private final double lying;
    /** Whether the other hand is on the edge of the block's top while it faces up - a table. */
    private final boolean edge;
    private final TableSurface surface;

    /** A hand on a table's edge: blocks to its side and towards the player off the middle, and pixels up. */
    private static final TableSurface DEFAULT_TABLE = new TableSurface(0, 1, 0, 1, 13 / 16.0, 3 / 16.0);

    /** What the block holds, what the player held, and the arm's swing. */
    private record Seen(BlockState block, String item, int count, String hand, boolean swinging, int swingTime) {
    }

    /** Where the item goes is the block's top, {@code top} pixels up. */
    public ItemRest(String blockClass, String accessor, double top) {
        this(blockClass, accessor, top, 0, -1, false);
    }

    private ItemRest(String blockClass, String accessor, double top, double inset, double lying, boolean edge) {
        this(blockClass, accessor, top, inset, lying, edge, DEFAULT_TABLE);
    }

    private ItemRest(String blockClass, String accessor, double top, double inset, double lying, boolean edge, TableSurface surface) {
        this.surface = surface;
        this.blockClass = blockClass;
        this.held = accessor.isEmpty() ? null : new ModAccess(accessor);
        this.top = top;
        this.inset = inset;
        this.lying = lying;
        this.edge = edge;
    }

    /** Where the item goes is the side the block faces (its {@code facing}). */
    static ItemRest front(String blockClass, String accessor) {
        return new ItemRest(blockClass, accessor, -1, 0, -1, false);
    }

    /** {@link #front}, and with the block facing up the hands wait on the edges of its top - a table. */
    static ItemRest table(String blockClass, String accessor) {
        return new ItemRest(blockClass, accessor, -1, 0, -1, true);
    }

    static ItemRest table(String blockClass, String accessor, TableSurface surface) {
        return new ItemRest(blockClass, accessor, -1, 0, -1, true, surface);
    }

    /** Where the item goes is the top: {@code standing} pixels up with the block upright, {@code lying} on its side - an hourglass. */
    static ItemRest upright(String blockClass, String accessor, double standing, double lying) {
        return new ItemRest(blockClass, accessor, standing, 0, lying, false);
    }

    /** Where the item goes is {@code inset} pixels in from the side the block faces - a shelf on a wall. */
    static ItemRest inside(String blockClass, String accessor, double inset) {
        return new ItemRest(blockClass, accessor, -1, inset, -1, false);
    }

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock().getClass().getName().equals(blockClass);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        if (supportSurface(block) == null && player.getMainHandItem().isEmpty() && count(player.level(), pos) == 0) return null;
        // At a table both hands wait on its edges; the one that puts or takes goes to the item then.
        Spot onEdge = edge(player, pos, block, true);
        return onEdge != null ? onEdge : rest(pos, block);
    }

    @Override
    public Spot supportHand(AbstractClientPlayer player, BlockPos pos, BlockState block) {
        return edge(player, pos, block, false);
    }

    /** A hand's place on the edge of a table that faces up - the main hand's side or the other; {@code null} for no table. */
    private Spot edge(AbstractClientPlayer player, BlockPos pos, BlockState block, boolean main) {
        if (!edge || facing(block) != Direction.UP) return null;
        Vec3 local = SubLevels.at(player.level(), pos).tickToLocal(player.position()).subtract(Vec3.atLowerCornerOf(pos));
        boolean right = Body.right(player, main);
        var p = surface.contact(local.x, local.z, right);
        return new Spot(new Vec3(p.x(), p.y(), p.z()).add(Vec3.atLowerCornerOf(pos)), Spots.UP);
    }

    @Override public TableSurface supportSurface(BlockState block) { return edge && facing(block) == Direction.UP ? surface : null; }
    @Override public boolean quietsSwing() { return edge; }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        ItemStack hand = player.getMainHandItem();
        return new Seen(block, item(level, pos), count(level, pos), hand.isEmpty() ? null : hand.getItem().toString(),
                player.swinging, player.swingTime);
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now)) return null;
        boolean came = now.count > 0 && (now.count > before.count || !now.item.equals(before.item));
        if (came) return now.item.equals(before.hand) ? new Gesture(rest(pos, now.block), Motion.PUT) : null;
        if (now.count < before.count) return before.hand == null ? new Gesture(rest(pos, now.block), Motion.TAKE) : null;
        // What the block holds is not always told at once, or at all (a packager, a cannon): the
        // arm swung by a use that did something is - the hand taps where the item goes.
        boolean swung = now.swinging && (!before.swinging || now.swingTime < before.swingTime);
        return swung ? new Gesture(rest(pos, now.block), Motion.TAP) : null;
    }

    /** How many items the block holds. */
    private int count(Level level, BlockPos pos) {
        Object value = holds(level, pos);
        if (value instanceof ItemStack stack) return stack.getCount();
        if (value instanceof Container container) {
            int count = 0;
            for (int i = 0; i < container.getContainerSize(); i++) count += container.getItem(i).getCount();
            return count;
        }
        if (!(value instanceof IItemHandler handler)) return 0;
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) count += handler.getStackInSlot(i).getCount();
        return count;
    }

    /** The first item the block holds; {@code null} with none. */
    private String item(Level level, BlockPos pos) {
        Object value = holds(level, pos);
        if (value instanceof ItemStack stack) return stack.isEmpty() ? null : stack.getItem().toString();
        if (value instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (!container.getItem(i).isEmpty()) return container.getItem(i).getItem().toString();
            }
            return null;
        }
        if (!(value instanceof IItemHandler handler)) return null;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) return handler.getStackInSlot(i).getItem().toString();
        }
        return null;
    }

    /** What the block's accessor gives; with no accessor, the block entity itself - a container. */
    private Object holds(Level level, BlockPos pos) {
        Object entity = level.getBlockEntity(pos);
        return held == null ? entity : held.read(entity);
    }

    private Spot rest(BlockPos pos, BlockState block) {
        Direction side = facing(block);
        if (top >= 0) return Spots.top(pos, 8, lying >= 0 && side.getAxis().isHorizontal() ? lying : top, 8);
        Spot spot = Spots.side(pos, side, 0.5);
        return inset == 0 ? spot : new Spot(spot.point().subtract(spot.out().scale(inset / 16)), spot.out());
    }

    /** The way the block faces; up with no {@code facing}. */
    private static Direction facing(BlockState block) {
        Property<?> facing = block.getBlock().getStateDefinition().getProperty("facing");
        return facing != null && block.getValue(facing) instanceof Direction d ? d : Direction.UP;
    }
}
