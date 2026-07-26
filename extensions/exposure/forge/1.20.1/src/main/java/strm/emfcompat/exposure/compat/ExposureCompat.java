package strm.emfcompat.exposure.compat;

import io.github.mortuusars.exposure.world.camera.Camera;
import io.github.mortuusars.exposure.world.camera.CameraInHand;
import io.github.mortuusars.exposure.world.camera.CameraOnStand;
import io.github.mortuusars.exposure.world.entity.CameraOperator;
import io.github.mortuusars.exposure.world.item.camera.CameraItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves which camera pose (if any) Exposure is currently applying to a player.
 * Mirrors the branch order of Exposure's own {@code HumanoidModelMixin}:
 * active camera (stand / selfie / aiming) first, disassembled camera in hand second.
 *
 * <p>For remote players the active camera is synced by Exposure's S2C packets, and the
 * camera ItemStack (with its data components) is synced by vanilla equipment tracking,
 * so every detector here is safe to call for any client-side player.</p>
 */
public final class ExposureCompat {

    public enum CameraPose {
        NONE,
        /** Camera raised to the face: both arms + head. */
        AIMING,
        /** Selfie mode: only the arm holding the camera. */
        SELFIE,
        /** Operating a tripod-mounted camera: both arms + head. */
        STAND,
        /** Camera held disassembled (attachment UI): both arms + head. */
        DISASSEMBLED
    }

    private ExposureCompat() {
    }

    /**
     * Returns the camera pose Exposure is applying to the given player, or {@link CameraPose#NONE}.
     */
    public static CameraPose getCameraPose(Player player) {
        if (player instanceof CameraOperator operator) {
            Camera camera = operator.getActiveExposureCamera();
            if (camera != null && camera.getItemStack().getItem() instanceof CameraItem item) {
                if (camera instanceof CameraOnStand) {
                    return CameraPose.STAND;
                }
                if (item.isInSelfieMode(camera.getItemStack())) {
                    return CameraPose.SELFIE;
                }
                return CameraPose.AIMING;
            }
        }

        // Inactive (lowered) camera in hand, disassembled for attachment management.
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof CameraItem item && item.isDisassembled(stack)) {
                return CameraPose.DISASSEMBLED;
            }
        }
        return CameraPose.NONE;
    }

    /**
     * Returns the model arm holding the active camera, using the same rule as Exposure:
     * the player's main-hand setting, flipped when the camera is in the off hand.
     * Only meaningful for {@link CameraPose#SELFIE}.
     */
    @Nullable
    public static HumanoidArm getCameraArm(Player player) {
        if (!(player instanceof CameraOperator operator)
                || !(operator.getActiveExposureCamera() instanceof CameraInHand cameraInHand)) {
            return null;
        }
        HumanoidArm mainArm = Minecraft.getInstance().options.mainHand().get();
        return cameraInHand.getHand() == InteractionHand.OFF_HAND ? mainArm.getOpposite() : mainArm;
    }
}
