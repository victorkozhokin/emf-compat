package strm.emfcompat.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores captured arm poses and tracks the current animation frame.
 * Modules that integrate animation mods call this class when poses should be captured or cleared.
 */
public final class PoseManager {

    private PoseManager() {
    }

    public static final Map<UUID, SavedPoses> entitySavedPoses = new HashMap<>();
    public static long currentFrame = 0;

    private static int cleanupCounter = 0;

    /**
     * Called once per render frame to remove stale entries for players that are no longer in the level.
     */
    public static void cleanupIfNeeded() {
        if (++cleanupCounter % 200 != 0) return;

        var mc = Minecraft.getInstance();
        if (mc.level == null) return;

        var activeUUIDs = mc.level.players().stream()
                .map(Player::getUUID)
                .collect(java.util.stream.Collectors.toSet());
        entitySavedPoses.keySet().retainAll(activeUUIDs);
    }

    /**
     * Marks the given limbs as active and snapshots their current poses from the model.
     */
    public static void setActiveParts(UUID uuid, ActiveParts activeParts, PlayerModel model) {
        PoseSnapshot leftArm = activeParts.leftArm() ? new PoseSnapshot(model.leftArm) : null;
        PoseSnapshot rightArm = activeParts.rightArm() ? new PoseSnapshot(model.rightArm) : null;

        Map<String, PoseSnapshot> parts = null;
        if (activeParts.leftLeg() || activeParts.rightLeg()) {
            parts = new HashMap<>();
            if (activeParts.leftLeg()) {
                parts.put("left_leg", new PoseSnapshot(model.leftLeg));
            }
            if (activeParts.rightLeg()) {
                parts.put("right_leg", new PoseSnapshot(model.rightLeg));
            }
        }

        savePoses(uuid, leftArm, rightArm, parts);
    }

    /**
     * Saves poses for the given player. Passing {@code null} for an arm skips that arm.
     */
    public static void savePoses(UUID uuid, PoseSnapshot leftArm, PoseSnapshot rightArm) {
        savePoses(uuid, leftArm, rightArm, null);
    }

    /**
     * Saves arm poses plus an optional full-body part map.
     */
    public static void savePoses(UUID uuid, PoseSnapshot leftArm, PoseSnapshot rightArm, Map<String, PoseSnapshot> parts) {
        entitySavedPoses.put(uuid, new SavedPoses(leftArm, rightArm, parts));
        currentFrame++;
    }

    /**
     * Removes any saved poses for the given player.
     */
    public static void clearPoses(UUID uuid) {
        entitySavedPoses.remove(uuid);
    }
}
