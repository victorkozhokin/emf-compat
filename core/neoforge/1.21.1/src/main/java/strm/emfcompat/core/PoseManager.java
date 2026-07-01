package strm.emfcompat.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Stores captured model poses and tracks the current animation frame.
 * <p>
 * Poses are keyed by the {@link Entity} instance rather than by UUID. This prevents
 * fake/dummy entities that reuse a player's UUID (Corpse dummies, Create contraption
 * fake players, etc.) from sharing or overwriting the real player's poses.
 * </p>
 */
public final class PoseManager {

    private PoseManager() {
    }

    private static final String DEFAULT_SOURCE = "default";

    public static final Map<Entity, SavedPoses> entitySavedPoses = new IdentityHashMap<>();
    public static final Map<Entity, Map<String, SavedPoses>> entitySavedPosesBySource = new IdentityHashMap<>();
    public static long currentFrame = 0;

    private static int cleanupCounter = 0;

    /**
     * Called once per render frame to remove stale entries for entities that are no longer in the level.
     */
    public static void cleanupIfNeeded() {
        if (++cleanupCounter % 200 != 0) return;

        var mc = Minecraft.getInstance();
        if (mc.level == null) return;

        entitySavedPoses.keySet().removeIf(entity -> !isEntityActive(entity));
        entitySavedPosesBySource.keySet().removeIf(entity -> !isEntityActive(entity));
    }

    private static boolean isEntityActive(Entity entity) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || entity == null) return false;
        if (mc.level.getEntity(entity.getId()) != entity) return false;
        if (entity.isRemoved() || !entity.isAlive()) return false;
        return true;
    }

    /**
     * Marks the given arm parts as active and snapshots their current poses from the model.
     */
    public static void setActiveParts(Entity entity, ActiveParts activeParts, PlayerModel model) {
        PoseSnapshot leftArm = activeParts.leftArm() ? new PoseSnapshot(model.leftArm) : null;
        PoseSnapshot rightArm = activeParts.rightArm() ? new PoseSnapshot(model.rightArm) : null;
        savePoses(entity, leftArm, rightArm);
    }

    /**
     * Saves poses for the given entity using the default source. Passing {@code null} for an arm skips that arm.
     */
    public static void savePoses(Entity entity, PoseSnapshot leftArm, PoseSnapshot rightArm) {
        savePoses(entity, DEFAULT_SOURCE, leftArm, rightArm);
    }

    /**
     * Saves poses for the given entity under the specified source. Passing {@code null} for an arm skips that arm.
     */
    public static void savePoses(Entity entity, String source, PoseSnapshot leftArm, PoseSnapshot rightArm) {
        savePoses(entity, source, leftArm, rightArm, null);
    }

    /**
     * Saves arm poses plus an optional full-body part map under the specified source.
     */
    public static void savePoses(Entity entity, String source, PoseSnapshot leftArm, PoseSnapshot rightArm, Map<String, PoseSnapshot> parts) {
        savePoses(entity, source, new SavedPoses(leftArm, rightArm, parts));
    }

    /**
     * Saves a complete {@link SavedPoses} instance under the specified source.
     */
    public static void savePoses(Entity entity, String source, SavedPoses poses) {
        savePoses(entity, source, poses, true);
    }

    /**
     * Saves a complete {@link SavedPoses} instance under the specified source, optionally incrementing the current
     * frame counter. Use {@code incrementFrame=false} when saving poses from inside an animation callback to avoid
     * interfering with per-frame deduplication logic in other mixins.
     */
    public static void savePoses(Entity entity, String source, SavedPoses poses, boolean incrementFrame) {
        if (entity == null) return;
        if (DEFAULT_SOURCE.equals(source)) {
            entitySavedPoses.put(entity, poses);
        } else {
            entitySavedPosesBySource
                    .computeIfAbsent(entity, k -> new HashMap<>())
                    .put(source, poses);
        }
        if (incrementFrame) {
            currentFrame++;
        }
    }

    /**
     * Removes any saved poses for the given entity from the default source.
     */
    public static void clearPoses(Entity entity) {
        clearPoses(entity, DEFAULT_SOURCE);
    }

    /**
     * Removes any saved poses for the given entity from the specified source.
     */
    public static void clearPoses(Entity entity, String source) {
        if (entity == null) return;
        if (DEFAULT_SOURCE.equals(source)) {
            entitySavedPoses.remove(entity);
        } else {
            var sources = entitySavedPosesBySource.get(entity);
            if (sources != null) {
                sources.remove(source);
                if (sources.isEmpty()) {
                    entitySavedPosesBySource.remove(entity);
                }
            }
        }
    }

    /**
     * Removes entries for the given source whose entity UUID is not present in the provided active UUID set.
     */
    public static void retainOnly(Collection<java.util.UUID> activeUUIDs, String source) {
        if (DEFAULT_SOURCE.equals(source)) {
            entitySavedPoses.entrySet().removeIf(entry -> !activeUUIDs.contains(entry.getKey().getUUID()));
        } else {
            var it = entitySavedPosesBySource.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                if (!activeUUIDs.contains(entry.getKey().getUUID())) {
                    var sources = entry.getValue();
                    sources.remove(source);
                    if (sources.isEmpty()) {
                        it.remove();
                    }
                }
            }
        }
    }

    /**
     * Returns the effective saved poses for the given entity, merging the default source and all named sources.
     * Named sources override the default per part; for arms, the latest named source with a non-null arm wins.
     */
    public static SavedPoses getSavedPoses(Entity entity) {
        SavedPoses defaultPoses = entitySavedPoses.get(entity);
        Map<String, SavedPoses> sources = entitySavedPosesBySource.get(entity);
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
    public static SavedPoses getSavedPoses(Entity entity, String source) {
        if (entity == null) return null;
        if (DEFAULT_SOURCE.equals(source)) {
            return entitySavedPoses.get(entity);
        }
        Map<String, SavedPoses> sources = entitySavedPosesBySource.get(entity);
        return sources != null ? sources.get(source) : null;
    }
}
