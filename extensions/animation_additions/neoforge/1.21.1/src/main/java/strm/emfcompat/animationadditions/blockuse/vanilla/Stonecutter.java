package strm.emfcompat.animationadditions.blockuse.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import strm.emfcompat.animationadditions.blockuse.*;

/**
 * A stonecutter: looked at, the hand waits over the saw; with our own player's screen open, a
 * block put on it is put down onto the saw, and taking a cut piece takes it from there. The saw
 * holds nothing in the world, so only our own open screen tells - other players get the wait.
 */
public final class Stonecutter implements BlockTarget {

    /** The top of the saw's base, pixels: the block to cut is laid on it. */
    private static final double SAW_TOP = 9;

    /** What is on the saw and what was cut, as our own screen has them. */
    private record Seen(BlockState block, int input, int result) {
    }

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof StonecutterBlock;
    }

    @Override
    public boolean menu() {
        return true;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return Spots.top(pos, 8, SAW_TOP, 8);
    }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        if (player == Minecraft.getInstance().player && player.containerMenu instanceof StonecutterMenu menu) {
            ItemStack input = menu.getSlot(StonecutterMenu.INPUT_SLOT).getItem();
            ItemStack result = menu.getSlot(StonecutterMenu.RESULT_SLOT).getItem();
            return new Seen(block, input.getCount(), result.getCount());
        }
        return new Seen(block, 0, 0);
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now)) return null;
        if (now.input > before.input) return new Gesture(Spots.top(pos, 8, SAW_TOP, 8), Motion.PUT);
        // A cut piece taken: the block on the saw goes down by one.
        if (now.input < before.input && before.result > 0) return new Gesture(Spots.top(pos, 8, SAW_TOP, 8), Motion.TAKE);
        return null;
    }
}
