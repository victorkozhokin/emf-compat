package strm.emfcompat.carryon.compat;

import net.minecraft.world.entity.player.Player;
import tschipp.carryon.Constants;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnDataManager;

public final class CarryOnCompat {
    private CarryOnCompat() {}

    public static boolean isCarrying(Player player) {
        if (player == null) return false;
        CarryOnData data = CarryOnDataManager.getCarryData(player);
        return data != null && data.isCarrying();
    }

    /** Mirrors the guards in Carry On's own HumanoidModel mixin. */
    public static boolean shouldRenderCarryPose(Player player) {
        return player != null
                && Constants.CLIENT_CONFIG.renderArms
                && isCarrying(player)
                && !player.isVisuallySwimming()
                && !player.isFallFlying();
    }

    /** Which arms Carry On's active script actually owns. */
    public static ActiveArms activeArms(Player player) {
        CarryOnData data = CarryOnDataManager.getCarryData(player);
        if (data != null && data.getActiveScript().isPresent()) {
            var render = data.getActiveScript().get().scriptRender();
            return new ActiveArms(render.renderLeftArm(), render.renderRightArm());
        }
        return new ActiveArms(true, true);
    }

    public record ActiveArms(boolean left, boolean right) {
    }
}
