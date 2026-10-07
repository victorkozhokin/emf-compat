package strm.emfcompat.animationadditions.blockuse.vanilla;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import strm.emfcompat.animationadditions.blockuse.*;

/**
 * A crafting table: looked at, the hand waits over the top; an ingredient put into a slot of the
 * grid, the hand puts it down on that slot of the top; one taken out, it takes it from there; a
 * craft (several slots going down at once), it takes the result from the middle.
 *
 * <p>What the grid holds is read from Visual Workbench (optional, by name) when it is there: its
 * table keeps the items in a block entity every client is sent, so this works for any player, on
 * the slots as that mod draws them (3 px apart, the grid turned to the player a quarter at a time,
 * {@code CraftingTableBlockEntityRenderer}). Without it only our own player's open crafting screen
 * tells, and the grid is turned the same way from where the player stands.</p>
 */
public final class CraftingTable implements BlockTarget {

    private static final String VISUAL_BLOCK = "fuzs.visualworkbench.world.level.block.CraftingTableWithInventoryBlock";
    private static final int SLOTS = 9;
    /** The slots' height over the top: Visual Workbench's flat items lie at 1.005, its floating ones at ~1.09. */
    private static final double TOP = 1.0;

    /** Each slot's item and count, what a use changes; empty when nothing tells. */
    private record Seen(BlockState block, String[] items, int[] counts) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Seen s && s.block == block && Arrays.equals(s.items, items) && Arrays.equals(s.counts, counts);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(counts);
        }
    }

    private static Method controller;
    private static Field angle;
    private static boolean looked;

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof CraftingTableBlock || block.getBlock().getClass().getName().equals(VISUAL_BLOCK);
    }

    @Override
    public boolean menu() {
        return true;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return Spots.top(pos, 8, 16, 8);
    }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        String[] items = new String[SLOTS];
        int[] counts = new int[SLOTS];
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof Container grid && grid.getContainerSize() == SLOTS) {
            for (int i = 0; i < SLOTS; i++) put(items, counts, i, grid.getItem(i));
        } else if (player == Minecraft.getInstance().player && player.containerMenu instanceof CraftingMenu menu) {
            // Our own screen: slot 0 is the result, 1..9 the grid.
            for (int i = 0; i < SLOTS; i++) put(items, counts, i, menu.getSlot(i + 1).getItem());
        }
        return new Seen(block, items, counts);
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now)) return null;
        int grew = -1, shrank = -1, shrinking = 0;
        for (int i = 0; i < SLOTS; i++) {
            boolean same = java.util.Objects.equals(before.items[i], now.items[i]);
            if (now.counts[i] > before.counts[i] || !same && now.counts[i] > 0) grew = i;
            else if (now.counts[i] < before.counts[i]) {
                shrank = i;
                shrinking++;
            }
        }
        BlockState block = now.block;
        if (grew >= 0) return new Gesture(slot(pos, block, grew), Motion.PUT);
        // Several slots used up at once is a craft: the result is taken off the middle.
        if (shrinking > 1) return new Gesture(Spots.top(pos, 8, 16, 8), Motion.TAKE);
        if (shrank >= 0) return new Gesture(slot(pos, block, shrank), Motion.TAKE);
        return null;
    }

    private static void put(String[] items, int[] counts, int i, ItemStack stack) {
        items[i] = stack.isEmpty() ? null : stack.getItem().toString();
        counts[i] = stack.getCount();
    }

    /** Slot {@code i} of the grid on the top, as Visual Workbench draws it. */
    private static Spot slot(BlockPos pos, BlockState block, int i) {
        Vector3f off = new Vector3f((i % 3) * 3f / 16f + 0.3125f - 0.5f, 0f, (i / 3) * 3f / 16f + 0.3125f - 0.5f);
        new Quaternionf().rotationY((float) Math.toRadians(gridAngle(pos))).transform(off);
        return new Spot(new Vec3(pos.getX() + 0.5 + off.x, pos.getY() + TOP, pos.getZ() + 0.5 + off.z), Spots.UP);
    }

    /**
     * How far the grid is turned, degrees: Visual Workbench's own animated angle when its table is
     * there; otherwise its rule - a quarter turn at a time towards our player.
     */
    private static float gridAngle(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        BlockEntity entity = mc.level == null ? null : mc.level.getBlockEntity(pos);
        if (entity != null && entity.getClass().getName().startsWith("fuzs.visualworkbench")) {
            try {
                if (!looked) {
                    looked = true;
                    controller = entity.getClass().getMethod("getAnimationController");
                    angle = controller.getReturnType().getField("currentAngle");
                }
                if (angle != null) return angle.getFloat(controller.invoke(entity));
            } catch (ReflectiveOperationException ignored) {
                angle = null;
            }
        }
        if (mc.player == null) return 0f;
        Vec3 centre = pos.getCenter();
        double turn = (Math.atan2(-(mc.player.getX() - centre.x), -(mc.player.getZ() - centre.z)) + 3.9269908169872414) % (2 * Math.PI);
        return (int) (turn * 2 / Math.PI) * 90f;
    }
}
