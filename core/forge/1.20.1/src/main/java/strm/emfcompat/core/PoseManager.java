package strm.emfcompat.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
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

    private static final String DEFAULT_SOURCE = "default";

    public static final Map<UUID, SavedPoses> entitySavedPoses = new HashMap<>();
    public static final Map<UUID, Map<String, SavedPoses>> entitySavedPosesBySource = new HashMap<>();
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
        entitySavedPosesBySource.keySet().retainAll(activeUUIDs);
        // The inner maps are removed along with their owning UUID entries above.
        // Do NOT call retainAll on the inner keySets here: their keys are source
        // names (Strings), not UUIDs, so that would incorrectly wipe all named
        // sources every 200 frames.
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

        savePoses(uuid, DEFAULT_SOURCE, leftArm, rightArm, parts);
    }

    /**
     * Saves poses for the given player using the default source. Passing {@code null} for an arm skips that arm.
     */
    public static void savePoses(UUID uuid, PoseSnapshot leftArm, PoseSnapshot rightArm) {
        savePoses(uuid, DEFAULT_SOURCE, leftArm, rightArm);
    }

    /**
     * Saves poses for the given player under the specified source. Passing {@code null} for an arm skips that arm.
     */
    public static void savePoses(UUID uuid, String source, PoseSnapshot leftArm, PoseSnapshot rightArm) {
        savePoses(uuid, source, leftArm, rightArm, true);
    }

    /**
     * Saves poses for the given player under the specified source, optionally incrementing the current frame counter.
     * Use {@code incrementFrame=false} when saving poses from inside an animation callback to avoid interfering with
     * per-frame deduplication logic in other mixins.
     */
    public static void savePoses(UUID uuid, String source, PoseSnapshot leftArm, PoseSnapshot rightArm, boolean incrementFrame) {
        savePoses(uuid, source, new SavedPoses(leftArm, rightArm, null), incrementFrame);
    }

    /**
     * Saves arm poses plus an optional full-body part map under the specified source.
     */
    public static void savePoses(UUID uuid, String source, PoseSnapshot leftArm, PoseSnapshot rightArm, Map<String, PoseSnapshot> parts) {
        savePoses(uuid, source, new SavedPoses(leftArm, rightArm, parts));
    }

    /**
     * Saves a complete {@link SavedPoses} instance under the specified source.
     */
    public static void savePoses(UUID uuid, String source, SavedPoses poses) {
        savePoses(uuid, source, poses, true);
    }

    /**
     * Saves a complete {@link SavedPoses} instance under the specified source, optionally incrementing the current
     * frame counter. Use {@code incrementFrame=false} when saving poses from inside an animation callback to avoid
     * interfering with per-frame deduplication logic in other mixins.
     */
    public static void savePoses(UUID uuid, String source, SavedPoses poses, boolean incrementFrame) {
        if (DEFAULT_SOURCE.equals(source)) {
            entitySavedPoses.put(uuid, poses);
        } else {
            entitySavedPosesBySource
                    .computeIfAbsent(uuid, k -> new HashMap<>())
                    .put(source, poses);
        }
        if (incrementFrame) {
            currentFrame++;
        }
    }

    /**
     * Removes any saved poses for the given player from the default source.
     */
    public static void clearPoses(UUID uuid) {
        clearPoses(uuid, DEFAULT_SOURCE);
    }

    /**
     * Removes any saved poses for the given player from the specified source.
     */
    public static void clearPoses(UUID uuid, String source) {
        if (DEFAULT_SOURCE.equals(source)) {
            entitySavedPoses.remove(uuid);
        } else {
            var sources = entitySavedPosesBySource.get(uuid);
            if (sources != null) {
                sources.remove(source);
                if (sources.isEmpty()) {
                    entitySavedPosesBySource.remove(uuid);
                }
            }
        }
    }

    /**
     * Removes entries for the given source that are not present in the provided active UUID set.
     */
    public static void retainOnly(Collection<UUID> activeUUIDs, String source) {
        if (DEFAULT_SOURCE.equals(source)) {
            entitySavedPoses.keySet().retainAll(activeUUIDs);
        } else {
            var it = entitySavedPosesBySource.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                var sources = entry.getValue();
                sources.remove(source);
                if (sources.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    /**
     * Returns the effective saved poses for the given player, merging the default source and all named sources.
     */
    public static SavedPoses getSavedPoses(UUID uuid) {
        SavedPoses defaultPoses = entitySavedPoses.get(uuid);
        Map<String, SavedPoses> sources = entitySavedPosesBySource.get(uuid);
        if (sources == null || sources.isEmpty()) {
            return defaultPoses;
        }

        PoseSnapshot leftArm = defaultPoses != null ? defaultPoses.leftArm() : null;
        PoseSnapshot rightArm = defaultPoses != null ? defaultPoses.rightArm() : null;
        Map<String, PoseSnapshot> parts = new HashMap<>();
        if (defaultPoses != null && defaultPoses.parts() != null) {
            parts.putAll(defaultPoses.parts());
        }

        for (SavedPoses poses : sources.values()) {
            if (poses.leftArm() != null) {
                leftArm = poses.leftArm();
            }
            if (poses.rightArm() != null) {
                rightArm = poses.rightArm();
            }
            if (poses.parts() != null) {
                parts.putAll(poses.parts());
            }
        }

        return new SavedPoses(leftArm, rightArm, parts);
    }

    /**
     * Returns the saved poses from a single source without merging. Useful when callers need the pose that was
     * captured specifically for that source (for example the live EMF-animated arm pose).
     */
    public static SavedPoses getSavedPoses(UUID uuid, String source) {
        if (DEFAULT_SOURCE.equals(source)) {
            return entitySavedPoses.get(uuid);
        }
        Map<String, SavedPoses> sources = entitySavedPosesBySource.get(uuid);
        return sources != null ? sources.get(source) : null;
    }
}
