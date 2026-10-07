package strm.emfcompat.animationadditions.blockuse.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import strm.emfcompat.animationadditions.blockuse.*;

/**
 * A crafter: looked at, the hand waits over its grid - the side the block's {@code top} is; with our
 * own player's screen open, an ingredient put into a slot is put down on that cell, one taken out
 * is taken from it, and a slot switched off or on is tapped. The crafter sends what it holds to no
 * one but the player whose screen is open, so other players get the wait.
 *
 * <p>The cells are 3.2 px apart on the grid's side ({@code crafter_top}). Seen from the front (the
 * side the result comes out of) with the grid up, the screen's first row is the far one and its
 * first column the left one.</p>
 */
public final class Crafter implements BlockTarget {

    private static final int SLOTS = 9;
    private static final double CELL = 3.2 / 16;

    /** Each slot's count and whether it is switched off, as our own screen has them. */
    private record Seen(BlockState block, int[] counts, boolean[] off) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Seen s && s.block == block && Arrays.equals(s.counts, counts) && Arrays.equals(s.off, off);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(counts);
        }
    }

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof CrafterBlock;
    }

    @Override
    public boolean menu() {
        return true;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return cell(pos, block, 4);
    }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        int[] counts = new int[SLOTS];
        boolean[] off = new boolean[SLOTS];
        if (player == Minecraft.getInstance().player && player.containerMenu instanceof CrafterMenu menu) {
            for (int i = 0; i < SLOTS; i++) {
                counts[i] = menu.getSlot(i).getItem().getCount();
                off[i] = menu.isSlotDisabled(i);
            }
        }
        return new Seen(block, counts, off);
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now)) return null;
        for (int i = 0; i < SLOTS; i++) {
            if (now.counts[i] > before.counts[i]) return new Gesture(cell(pos, now.block, i), Motion.PUT);
            if (now.counts[i] < before.counts[i]) return new Gesture(cell(pos, now.block, i), Motion.TAKE);
            if (now.off[i] != before.off[i]) return new Gesture(cell(pos, now.block, i), Motion.TAP);
        }
        return null;
    }

    /** Cell {@code i} of the grid, on the side the block's top is. */
    private static Spot cell(BlockPos pos, BlockState block, int i) {
        FrontAndTop orientation = block.getValue(BlockStateProperties.ORIENTATION);
        Vec3 front = Vec3.atLowerCornerOf(orientation.front().getNormal());
        Vec3 top = Vec3.atLowerCornerOf(orientation.top().getNormal());
        // The first column's side, and the first row's: away from the front.
        Vec3 left = front.cross(top);
        Vec3 point = Vec3.atCenterOf(pos).add(top.scale(0.5))
                .add(left.scale((1 - i % 3) * CELL)).add(front.scale(-(1 - i / 3) * CELL));
        return new Spot(point, top);
    }
}
