package strm.emfcompat.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores captured model poses and tracks the current animation frame.
 * Modules that integrate animation mods call this class when poses should be captured or cleared.
 */
public final class PoseManager {

    private PoseManager() {
    }

    /** Name of the unnamed source used by {@link #setActiveParts} and the two-arg {@code savePoses}. */
    public static final String DEFAULT_SOURCE = "default";

    public static final Map<UUID, SavedPoses> entitySavedPoses = new HashMap<>();
    public static final Map<UUID, Map<String, SavedPoses>> entitySavedPosesBySource = new HashMap<>();
    public static long currentFrame = 0;

    // Per-source merge priority: when several sources pose the same part, the higher priority wins.
    // Default (unregistered) is 0. "Seat" poses like horse-riding register a negative priority so
    // action poses (guns, attacks) take the arms while the seat keeps the legs/body.
    private static final Map<String, Integer> sourcePriority = new HashMap<>();

    // The body-follow translation delta the core applied to a player's arms this frame
    // (model-space pixels, currentBody - bodyBase). Exposed so consumers can move objects
    // attached to the hands by the same amount, keeping them in sync with the arms.
    private static final Map<UUID, org.joml.Vector3f> bodyFollowDelta = new HashMap<>();

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
        bodyFollowDelta.keySet().retainAll(activeUUIDs);
        // The inner maps are removed along with their owning UUID entries above.
        // Do NOT call retainAll on the inner keySets here: their keys are source
        // names (Strings), not UUIDs, so that would incorrectly wipe all named
        // sources every 200 frames.
    }

    /**
     * Records the body-follow translation delta applied to the given player's arms this
     * frame. Passing {@code null} clears it. Called by the core restore.
     */
    public static void setBodyFollowDelta(UUID uuid, org.joml.Vector3f delta) {
        if (delta == null) {
            bodyFollowDelta.remove(uuid);
        } else {
            bodyFollowDelta.put(uuid, delta);
        }
    }

    /**
     * Returns the body-follow translation delta the core applied to the player's arms this
     * frame (model-space pixels), or {@code null} if the player has no body-follow pose.
     * Consumers can apply the same delta to hand-attached objects to keep them in sync.
     */
    public static org.joml.Vector3f getBodyFollowDelta(UUID uuid) {
        if (!EMFCompatCore.isCompatEnabled()) return null;
        return bodyFollowDelta.get(uuid);
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
        savePoses(uuid, source, leftArm, rightArm, null);
    }

    /**
     * Saves arm poses plus an optional full-body part map under the specified source.
     */
    public static void savePoses(UUID uuid, String source, PoseSnapshot leftArm, PoseSnapshot rightArm, Map<String, PoseSnapshot> parts) {
        savePoses(uuid, source, new SavedPoses(leftArm, rightArm, parts));
    }

    /**
     * Saves arm poses (following the body), an optional part map and the body's neutral
     * position under the specified source. With a non-null {@code bodyBase}, the arms are
     * restored rotation-absolute plus a position offset equal to the body's movement since
     * capture, so the pose follows the torso. See {@link SavedPoses}.
     */
    public static void savePoses(UUID uuid, String source, PoseSnapshot leftArm, PoseSnapshot rightArm,
                                 Map<String, PoseSnapshot> parts, org.joml.Vector3f bodyBase) {
        savePoses(uuid, source, new SavedPoses(leftArm, rightArm, parts, bodyBase));
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
     * Registers a merge priority for a named source (higher wins per part; default is 0). Seat-type
     * poses (e.g. horse riding) register a negative value so action poses take the arms.
     */
    public static void setSourcePriority(String source, int priority) {
        sourcePriority.put(source, priority);
    }

    private static int priorityOf(String source) {
        return sourcePriority.getOrDefault(source, 0);
    }

    /**
     * Returns {@code true} if any source other than {@code excludeSource} currently poses either
     * arm for this player (via the arm slot or the {@code parts} map) — i.e. an action pose such as
     * a gun aim or a melee swing is active. A "seat" pose (horse riding) uses this to yield its
     * whole upper body while an action controls the arms.
     */
    public static boolean hasArmPoseExcept(UUID uuid, String excludeSource) {
        if (!EMFCompatCore.isCompatEnabled()) return false;
        SavedPoses def = entitySavedPoses.get(uuid);
        if (def != null && emfcompat$armPosed(def)) {
            return true;
        }
        Map<String, SavedPoses> sources = entitySavedPosesBySource.get(uuid);
        if (sources != null) {
            for (Map.Entry<String, SavedPoses> e : sources.entrySet()) {
                if (!e.getKey().equals(excludeSource) && emfcompat$armPosed(e.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean emfcompat$armPosed(SavedPoses poses) {
        if (poses.leftArm() != null || poses.rightArm() != null) {
            return true;
        }
        Map<String, PoseSnapshot> parts = poses.parts();
        return parts != null && (parts.containsKey("left_arm") || parts.containsKey("right_arm"));
    }

    /**
     * Returns the effective saved poses for the given player, merging the default source and all
     * named sources in ascending priority order (higher priority overrides lower).
     *
     * <p>Each arm has a single winner — the highest-priority source that poses it, via either the
     * arm <em>slot</em> (rotation + optional body-follow) or the {@code parts} map (full pose). The
     * winner's channel is preserved and the other channel cleared, so e.g. a gun/attack slot pose
     * overrides a riding-seat parts pose for the arms while the seat keeps the legs/body.</p>
     */
    public static SavedPoses getSavedPoses(UUID uuid) {
        // Global switch: hand out nothing, so every restore site becomes a no-op even if an
        // addon keeps capturing.
        if (!EMFCompatCore.isCompatEnabled()) return null;
        SavedPoses defaultPoses = entitySavedPoses.get(uuid);
        Map<String, SavedPoses> sources = entitySavedPosesBySource.get(uuid);
        if (sources == null || sources.isEmpty()) {
            return defaultPoses;
        }
        // Fast path (the common active case): a single named source and no default — return it
        // directly, no allocation or sort. A single-source merge produces the same result, and no
        // addon poses an arm through both the slot and the parts map at once. Consumers only read
        // the result, so sharing the reference is safe.
        if (defaultPoses == null && sources.size() == 1) {
            return sources.values().iterator().next();
        }

        Map<String, PoseSnapshot> parts = new HashMap<>();
        PoseSnapshot leftArm = null;
        PoseSnapshot rightArm = null;
        org.joml.Vector3f bodyBase = null;

        // The default source is the lowest-priority base.
        if (defaultPoses != null) {
            if (defaultPoses.parts() != null) parts.putAll(defaultPoses.parts());
            leftArm = defaultPoses.leftArm();
            rightArm = defaultPoses.rightArm();
            bodyBase = defaultPoses.bodyBase();
        }

        // Only allocate + sort when there's actually more than one named source to order.
        Collection<Map.Entry<String, SavedPoses>> ordered;
        if (sources.size() > 1) {
            List<Map.Entry<String, SavedPoses>> list = new ArrayList<>(sources.entrySet());
            // Ascending priority; ties broken by source name so the merge is fully deterministic
            // (never relies on HashMap iteration order).
            list.sort(Comparator
                    .comparingInt((Map.Entry<String, SavedPoses> e) -> priorityOf(e.getKey()))
                    .thenComparing(Map.Entry::getKey));
            ordered = list;
        } else {
            ordered = sources.entrySet();
        }

        for (Map.Entry<String, SavedPoses> entry : ordered) {
            SavedPoses poses = entry.getValue();
            Map<String, PoseSnapshot> sp = poses.parts();
            if (sp != null) {
                parts.putAll(sp);
            }

            // Arms: whichever channel this source used wins and clears the other channel.
            if (sp != null && sp.containsKey("left_arm")) {
                leftArm = null;                       // parts channel wins for the left arm
            } else if (poses.leftArm() != null) {
                leftArm = poses.leftArm();            // slot channel wins
                parts.remove("left_arm");
            }
            if (sp != null && sp.containsKey("right_arm")) {
                rightArm = null;
            } else if (poses.rightArm() != null) {
                rightArm = poses.rightArm();
                parts.remove("right_arm");
            }

            if (poses.bodyBase() != null) {
                bodyBase = poses.bodyBase();
            }
        }

        return new SavedPoses(leftArm, rightArm, parts, bodyBase);
    }

    /**
     * Returns the saved poses from a single source without merging. Useful when callers need the pose that was
     * captured specifically for that source (for example the live EMF-animated arm pose).
     */
    public static SavedPoses getSavedPoses(UUID uuid, String source) {
        if (!EMFCompatCore.isCompatEnabled()) return null;
        if (DEFAULT_SOURCE.equals(source)) {
            return entitySavedPoses.get(uuid);
        }
        Map<String, SavedPoses> sources = entitySavedPosesBySource.get(uuid);
        return sources != null ? sources.get(source) : null;
    }
}
