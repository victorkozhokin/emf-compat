package strm.emfcompat.watut.compat;

import com.corosus.watut.PlayerStatus;
import com.corosus.watut.PlayerStatusManagerClient;
import com.corosus.watut.WatutMod;
import com.corosus.watut.config.ConfigClient;
import com.corosus.watut.config.ConfigServerControlledSyncedToClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves which parts (if any) WATUT is currently posing on a player.
 * Mirrors the activation conditions of WATUT's own {@code setupRotationsHook}:
 * master config flags, the first-person / own-inventory exclusions, and the
 * per-feature flags for typing and idle.
 *
 * <p>WATUT lerps its poses smoothly on its own, so the captured snapshots are
 * already smooth - no extra interpolation is needed on our side.</p>
 */
public final class WatutCompat {

    /**
     * Which parts of the model WATUT is posing.
     *
     * @param arms both arms (GUI reach / typing / transition lerp)
     * @param head head (GUI look-at-screen / idle droop / transition lerp)
     */
    public record WatutPose(boolean arms, boolean head) {
    }

    private WatutCompat() {
    }

    /**
     * Returns the parts WATUT is posing on the given player, or {@code null} if
     * WATUT is not affecting the model right now (and saved poses should be cleared).
     */
    @Nullable
    public static WatutPose getPosedParts(Player player) {
        if (!ConfigClient.showPlayerAnimations || !ConfigServerControlledSyncedToClient.showPlayerAnimations) {
            return null;
        }

        PlayerStatusManagerClient manager = WatutMod.getPlayerStatusManagerClient();
        if (manager == null || !manager.shouldAnimate(player)) {
            return null;
        }

        // WATUT does not pose the local player's own-inventory paper doll.
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player && mc.screen instanceof InventoryScreen && player.isAlive()) {
            return null;
        }

        PlayerStatus status = manager.getStatus(player);
        if (status == null) {
            return null;
        }

        boolean gui = ConfigClient.showPlayerAnimation_Gui
                && ConfigServerControlledSyncedToClient.showPlayerAnimation_Gui
                && status.getPlayerGuiState() != PlayerStatus.PlayerGuiState.NONE;
        boolean typing = ConfigClient.showPlayerAnimation_Typing
                && ConfigServerControlledSyncedToClient.showPlayerAnimation_Typing
                && status.getPlayerChatState() == PlayerStatus.PlayerChatState.CHAT_TYPING;
        boolean idle = ConfigClient.showPlayerAnimation_Idle
                && ConfigServerControlledSyncedToClient.showPlayerAnimation_Idle
                && status.isIdle();
        // WATUT keeps adjusting the model while its transition lerp runs;
        // keep capturing both groups so the lerp plays out instead of snapping.
        boolean lerping = status.isLerping();

        if (!gui && !typing && !idle && !lerping) {
            return null;
        }
        return new WatutPose(gui || typing || lerping, gui || idle || lerping);
    }
}
