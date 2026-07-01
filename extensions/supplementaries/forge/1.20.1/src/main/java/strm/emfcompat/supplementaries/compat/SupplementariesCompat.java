package strm.emfcompat.supplementaries.compat;

import net.mehvahdjukaar.supplementaries.common.items.BubbleBlowerItem;
import net.mehvahdjukaar.supplementaries.common.items.FluteItem;
import net.mehvahdjukaar.supplementaries.common.items.SlingshotItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class SupplementariesCompat {

    private SupplementariesCompat() {
    }

    public static boolean isUsingSupplementariesItem(LivingEntity entity) {
        if (entity == null) return false;
        ItemStack useItem = entity.getUseItem();
        if (useItem.isEmpty()) return false;
        Item item = useItem.getItem();
        return item instanceof FluteItem
                || item instanceof SlingshotItem
                || item instanceof BubbleBlowerItem;
    }
}
