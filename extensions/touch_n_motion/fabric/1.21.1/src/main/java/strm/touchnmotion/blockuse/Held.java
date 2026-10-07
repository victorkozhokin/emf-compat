package strm.touchnmotion.blockuse;

import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * What a mod's block holds, read without the loader's own inventory type: anything that answers
 * to {@code getSlots()} and {@code getStackInSlot(int)} - NeoForge's item handler, and what Fabric
 * ports of the same mods carry in its place - is read by those two calls. Anything else holds nothing.
 */
public final class Held {
    private Held() {
    }

    /** The stacks an inventory holds; empty for what is not one. */
    public static ItemStack[] stacks(Object inventory) {
        if (inventory == null) return new ItemStack[0];
        try {
            Method slots = inventory.getClass().getMethod("getSlots");
            Method stack = inventory.getClass().getMethod("getStackInSlot", int.class);
            int n = (Integer) slots.invoke(inventory);
            ItemStack[] out = new ItemStack[n];
            for (int i = 0; i < n; i++) out[i] = stack.invoke(inventory, i) instanceof ItemStack s ? s : ItemStack.EMPTY;
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new ItemStack[0];
        }
    }

    /** How much fluid a tank holds, by its {@code getFluidAmount()}; zero for what has none. */
    public static long fluid(Object tank) {
        if (tank == null) return 0;
        try {
            return ((Number) tank.getClass().getMethod("getFluidAmount").invoke(tank)).longValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }
}
