package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A block of Create's that one item rests on, put there and taken back by hand - a depot: with something in the hand, or something on the block, the hand goes over its top; the
 * item put there, the hand puts it down; taken, it takes it. Optional: the block and its item are
 * found by name, and the item is every client's to see - this is any player's.
 *
 * <p>A belt or a funnel changes what is on it too: it is a hand's doing only when the item that
 * came is the one the player held, or when it went with the hand empty.</p>
 */
final class ItemRest implements BlockTarget {

    private final String blockClass;
    private final ModAccess held;
    /** The top the item lies on, pixels. */
    private final double top;

    /** What lies on the block, and what the player held. */
    private record Seen(BlockState block, String item, int count, String hand) {
    }

    ItemRest(String blockClass, String getter, double top) {
        this.blockClass = blockClass;
        this.held = new ModAccess(getter);
        this.top = top;
    }

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock().getClass().getName().equals(blockClass);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return player.getMainHandItem().isEmpty() && resting(player.level(), pos).isEmpty() ? null : rest(pos);
    }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        ItemStack stack = resting(level, pos);
        ItemStack hand = player.getMainHandItem();
        return new Seen(block, stack.isEmpty() ? null : stack.getItem().toString(), stack.getCount(),
                hand.isEmpty() ? null : hand.getItem().toString());
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now)) return null;
        boolean came = now.count > 0 && (now.count > before.count || !now.item.equals(before.item));
        if (came) return now.item.equals(before.hand) ? new Gesture(rest(pos), Motion.PUT) : null;
        return now.count < before.count && before.hand == null ? new Gesture(rest(pos), Motion.TAKE) : null;
    }

    private ItemStack resting(Level level, BlockPos pos) {
        return held.read(level.getBlockEntity(pos)) instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }

    private Spot rest(BlockPos pos) {
        return Spots.top(pos, 8, top, 8);
    }
}
