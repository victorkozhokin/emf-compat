package strm.emfcompat.animationadditions.blockuse.create;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.IItemHandler;
import strm.emfcompat.animationadditions.blockuse.*;

/**
 * Create's basin: items go into it thrown, not by hand - an empty hand takes all of them out. With
 * an empty hand and something in the basin the hand goes down into it; emptied, it takes. Optional,
 * by name; what the basin holds is every client's to see.
 */
public final class Basin implements BlockTarget {

    private static final ModBlock BLOCK = ModBlock.exact("com.simibubi.create.content.processing.basin.BasinBlock", "create:basin");
    private static final ModAccess INPUT = new ModAccess("getInputInventory");
    private static final ModAccess OUTPUT = new ModAccess("getOutputInventory");
    /** Inside, under the rim, pixels. */
    private static final double INSIDE = 11;

    /** How many items are in it, and whether the player's hand is empty. */
    private record Seen(BlockState block, int items, boolean emptyHand) {
    }

    @Override
    public boolean matches(BlockState block) {
        return BLOCK.is(block);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return player.getMainHandItem().isEmpty() && items(player.level(), pos) > 0 ? Spots.top(pos, 8, INSIDE, 8) : null;
    }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        return new Seen(block, items(level, pos), player.getMainHandItem().isEmpty());
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now)) return null;
        // A recipe uses items up too, and leaves its result: a hand takes everything.
        return before.items > 0 && now.items == 0 && before.emptyHand ? new Gesture(Spots.top(pos, 8, INSIDE, 8), Motion.TAKE) : null;
    }

    private static int items(Level level, BlockPos pos) {
        Object entity = level.getBlockEntity(pos);
        return count(INPUT.read(entity)) + count(OUTPUT.read(entity));
    }

    private static int count(Object inventory) {
        if (!(inventory instanceof IItemHandler handler)) return 0;
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) count += handler.getStackInSlot(i).getCount();
        return count;
    }
}
