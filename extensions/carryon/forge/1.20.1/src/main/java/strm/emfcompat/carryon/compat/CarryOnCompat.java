package strm.emfcompat.carryon.compat;

import net.minecraft.world.entity.player.Player;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnDataManager;

public final class CarryOnCompat {
    private CarryOnCompat() {}

    public static boolean isCarrying(Player player) {
        if (player == null) return false;
        CarryOnData data = CarryOnDataManager.getCarryData(player);
        return data != null && data.isCarrying();
    }
}
