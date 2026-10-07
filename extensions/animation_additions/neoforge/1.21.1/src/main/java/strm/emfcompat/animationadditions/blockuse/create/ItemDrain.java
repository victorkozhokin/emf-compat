package strm.emfcompat.animationadditions.blockuse.create;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import strm.emfcompat.animationadditions.blockuse.*;

/**
 * Create's item drain: a bucket or a bottle used on it is poured out into it from the hand - the
 * item never lies on it; one a belt left there, an empty hand takes. With something to pour in the
 * hand, or something on the drain, the hand goes over its top; the fluid in it going up, it pours
 * (a put); the item gone with the hand empty, it takes. Optional, by name; the drain's tank and
 * item are every client's to see.
 */
public final class ItemDrain implements BlockTarget {

    private static final String BLOCK = "com.simibubi.create.content.fluids.drain.ItemDrainBlock";
    private static final ModAccess HELD = new ModAccess("getHeldItemStack");
    private static final ModAccess HANDLER = new ModAccess("getPrimaryHandler");
    /** The grate the item lies on, pixels. */
    private static final double TOP = 13;

    private static Field tank;
    private static boolean failed;

    /** What lies on it, how much fluid is in it, and whether the player's hand is empty. */
    private record Seen(BlockState block, int items, int fluid, boolean emptyHand) {
    }

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock().getClass().getName().equals(BLOCK);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        ItemStack hand = player.getMainHandItem();
        boolean pours = !hand.isEmpty() && !(hand.getItem() instanceof BlockItem);
        return pours || hand.isEmpty() && items(player.level(), pos) > 0 ? Spots.top(pos, 8, TOP, 8) : null;
    }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        return new Seen(block, items(level, pos), fluid(level.getBlockEntity(pos)), player.getMainHandItem().isEmpty());
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now)) return null;
        if (now.fluid > before.fluid && !before.emptyHand) return new Gesture(Spots.top(pos, 8, TOP, 8), Motion.PUT);
        return now.items < before.items && before.emptyHand ? new Gesture(Spots.top(pos, 8, TOP, 8), Motion.TAKE) : null;
    }

    private static int items(Level level, BlockPos pos) {
        return HELD.read(level.getBlockEntity(pos)) instanceof ItemStack stack ? stack.getCount() : 0;
    }

    /** Millibuckets in the drain's own tank ({@code internalTank}). */
    private static int fluid(BlockEntity entity) {
        if (entity == null || failed) return 0;
        try {
            if (tank == null) {
                tank = entity.getClass().getDeclaredField("internalTank");
                tank.setAccessible(true);
            }
            return HANDLER.read(tank.get(entity)) instanceof FluidTank handler ? handler.getFluidAmount() : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            LoggerFactory.getLogger("EMFCompatBlockUse").warn("[BlockUse] cannot read the item drain's tank", e);
            return 0;
        }
    }
}
