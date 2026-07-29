package strm.emfcompat.core;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generic per-entity body-part synchronisation helper.
 *
 * <p>Extensions can capture a "base" pose (e.g. the pose set by the game or by
 * another mod) and a "current" pose (e.g. the same part after an animation mod
 * has modified it) and then query the delta. This is useful for moving attached
 * objects (held blocks, carried entities, accessories, etc.) with animated body
 * parts without hard-coding transform logic in every extension.</p>
 */
public final class BodyPartSync {

    private BodyPartSync() {
    }

    private static final Map<UUID, Map<String, State>> STATES = new HashMap<>();

    /**
     * Stores the base pose of {@code part} for the given entity and part name.
     */
    public static void captureBase(UUID uuid, String partName, ModelPart part) {
        getState(uuid, partName).base = new PoseSnapshot(part);
    }

    /**
     * Stores the current/live pose of {@code part} for the given entity and part name.
     */
    public static void captureCurrent(UUID uuid, String partName, ModelPart part) {
        getState(uuid, partName).current = new PoseSnapshot(part);
    }

    /**
     * Returns the stored base pose, or {@code null} if none was captured.
     */
    public static PoseSnapshot getBase(UUID uuid, String partName) {
        State state = getStateOrNull(uuid, partName);
        return state == null ? null : state.base;
    }

    /**
     * Returns the stored current pose, or {@code null} if none was captured.
     */
    public static PoseSnapshot getCurrent(UUID uuid, String partName) {
        State state = getStateOrNull(uuid, partName);
        return state == null ? null : state.current;
    }

    /**
     * Returns {@code true} if both a base and a current pose exist for the part.
     */
    public static boolean hasDelta(UUID uuid, String partName) {
        State state = getStateOrNull(uuid, partName);
        return state != null && state.base != null && state.current != null;
    }

    /**
     * Returns the positional delta (current - base) in model pixels.
     */
    public static Vector3f getTranslationDelta(UUID uuid, String partName) {
        State state = requireState(uuid, partName);
        return new Vector3f(
                state.current.x - state.base.x,
                state.current.y - state.base.y,
                state.current.z - state.base.z
        );
    }

    /**
     * Returns the rotational delta (current - base), with angles normalised to
     * the [-π, π] range to avoid 360° wrap flips.
     */
    public static Vector3f getRotationDelta(UUID uuid, String partName) {
        State state = requireState(uuid, partName);
        return new Vector3f(
                angleDelta(state.current.xRot, state.base.xRot),
                angleDelta(state.current.yRot, state.base.yRot),
                angleDelta(state.current.zRot, state.base.zRot)
        );
    }

    /**
     * Removes all tracked parts for the given entity.
     */
    public static void clear(UUID uuid) {
        STATES.remove(uuid);
    }

    /**
     * Removes a single tracked part for the given entity.
     */
    public static void clearPart(UUID uuid, String partName) {
        Map<String, State> map = STATES.get(uuid);
        if (map == null) return;
        map.remove(partName);
        if (map.isEmpty()) {
            STATES.remove(uuid);
        }
    }

    /**
     * Removes all tracked data.
     */
    public static void clearAll() {
        STATES.clear();
    }

    /**
     * Normalises an angular delta to the [-π, π] range.
     */
    public static float angleDelta(float current, float base) {
        float delta = current - base;
        while (delta > (float) Math.PI) delta -= 2.0f * (float) Math.PI;
        while (delta < -(float) Math.PI) delta += 2.0f * (float) Math.PI;
        return delta;
    }

    private static State requireState(UUID uuid, String partName) {
        return getState(uuid, partName);
    }

    private static State getState(UUID uuid, String partName) {
        return STATES.computeIfAbsent(uuid, k -> new HashMap<>())
                .computeIfAbsent(partName, k -> new State());
    }

    private static State getStateOrNull(UUID uuid, String partName) {
        Map<String, State> map = STATES.get(uuid);
        return map == null ? null : map.get(partName);
    }

    private static final class State {
        PoseSnapshot base;
        PoseSnapshot current;
    }
}
