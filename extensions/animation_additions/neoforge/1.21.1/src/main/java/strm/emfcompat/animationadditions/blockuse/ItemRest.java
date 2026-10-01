package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A block of a mod's that items are put on or into by hand and taken back - Create's depot,
 * mechanical crafter, deployer, packager, frogport; Simulated's navigation table; Aeronautics'
 * mounted potato cannon: with something in the hand, or something in the block, the hand goes to
 * where the item goes - the top, or the side the block faces; the item put there, the hand puts
 * it; taken, it takes it. Optional: the block and what it holds are found by name (a method or a
 * field giving an {@code ItemStack} or an item handler), and what it holds is every client's to
 * see - this is any player's.
 *
 * <p>A belt, a funnel, the block's own work change what it holds too: it is a hand's doing only
 * when the item that came is the one the player held, or when it went with the hand empty. A use
 * that shows in nothing the block holds is still a tap: the arm swings.</p>
 */
final class ItemRest implements BlockTarget {

    private final String blockClass;
    private final ModAccess held;
    /** The top the item lies on, pixels; negative for the middle of the side the block faces. */
    private final double top;

    /** What the block holds, what the player held, and the arm's swing. */
    private record Seen(BlockState block, String item, int count, String hand, boolean swinging, int swingTime) {
    }

    /** Where the item goes is the block's top, {@code top} pixels up. */
    ItemRest(String blockClass, String accessor, double top) {
        this.blockClass = blockClass;
        this.held = new ModAccess(accessor);
        this.top = top;
    }

    /** Where the item goes is the side the block faces (its {@code facing}). */
    static ItemRest front(String blockClass, String accessor) {
        return new ItemRest(blockClass, accessor, -1);
    }

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock().getClass().getName().equals(blockClass);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return player.getMainHandItem().isEmpty() && count(player.level(), pos) == 0 ? null : rest(pos, block);
    }

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
        Object value = held.read(level.getBlockEntity(pos));
        if (value instanceof ItemStack stack) return stack.getCount();
        if (!(value instanceof IItemHandler handler)) return 0;
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) count += handler.getStackInSlot(i).getCount();
        return count;
    }

    /** The first item the block holds; {@code null} with none. */
    private String item(Level level, BlockPos pos) {
        Object value = held.read(level.getBlockEntity(pos));
        if (value instanceof ItemStack stack) return stack.isEmpty() ? null : stack.getItem().toString();
        if (!(value instanceof IItemHandler handler)) return null;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) return handler.getStackInSlot(i).getItem().toString();
        }
        return null;
    }

    private Spot rest(BlockPos pos, BlockState block) {
        if (top >= 0) return Spots.top(pos, 8, top, 8);
        Property<?> facing = block.getBlock().getStateDefinition().getProperty("facing");
        Direction side = facing != null && block.getValue(facing) instanceof Direction d ? d : Direction.UP;
        return Spots.side(pos, side, 0.5);
    }
}
