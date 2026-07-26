package strm.emfcompat.tacz.compat;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Decides whether TACZ is currently posing the player's arms, mirroring the
 * conditions of TACZ's own {@code InnerThirdPersonManager.setRotationAnglesHead}
 * (the single funnel behind its {@code HumanoidModel.setupAnim} TAIL mixin).
 *
 * <p>TACZ poses the model whenever a gun with a loaded display is held in the
 * main hand, except while paused, sleeping, climbing, swimming or elytra-flying —
 * in those states it deliberately keeps the vanilla pose, so we must not
 * capture either (otherwise the vanilla swim/fly arm pose would stick to the
 * EMF model).</p>
 */
public final class TACZCompat {

    private TACZCompat() {
    }

    public static boolean isGunPoseActive(Player player) {
        // TACZ's setRotationAnglesHead returns early while the game is paused; the world
        // still renders, so without this we would freeze a stale gun pose onto the model.
        if (Minecraft.getInstance().isPaused()) {
            return false;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (IGun.getIGunOrNull(mainHand) == null) {
            return false;
        }
        if (player.getPose() == Pose.SLEEPING
                || player.onClimbable()
                || player.isSwimming()
                || player.getPose() == Pose.FALL_FLYING) {
            return false;
        }
        // The client-side gun display must be loaded; without it TACZ poses nothing.
        return TimelessAPI.getGunDisplay(mainHand).isPresent();
    }

    /**
     * Returns {@code true} if playerAnimator is installed and is driving a third-person
     * gun animation for the player's held gun. In that case playerAnimator also poses the
     * head (to aim), so the head must be captured and restored after EMF; the vanilla-pose
     * fallback ({@code InnerThirdPersonManager}) does not move the head, so it is left alone.
     */
    public static boolean isPlayerAnimatorPosingHead(Player player) {
        try {
            if (!PlayerAnimatorCompat.isInstalled()) {
                return false;
            }
            java.util.Optional<GunDisplayInstance> display = TimelessAPI.getGunDisplay(player.getMainHandItem());
            return display.isPresent() && PlayerAnimatorCompat.hasPlayerAnimator3rd(player, display.get());
        } catch (Throwable t) {
            return false;
        }
    }
}
