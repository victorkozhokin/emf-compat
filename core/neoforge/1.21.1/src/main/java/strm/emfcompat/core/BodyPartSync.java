package strm.emfcompat.core;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Generic per-entity body-part synchronisation helper.
 *
 * <p>Extensions can capture a "base" pose (e.g. the pose set by the game or by
 * another mod) and a "current" pose (e.g. the same part after an animation mod
 * has modified it) and then query the delta. This is useful for moving attached
 * objects (held blocks, carried entities, accessories, etc.) with animated body
 * parts without hard-coding transform logic in every extension.</p>
 *
 * <p>Data is keyed by entity instance rather than UUID so fake/dummy entities
 * that reuse a player's UUID do not share deltas with the real player.</p>
 */
public final class BodyPartSync {

    private BodyPartSync() {
    }

    private static final Map<Entity, Map<String, State>> STATES = new IdentityHashMap<>();

    /**
     * Stores the base pose of {@code part} for the given entity and part name.
     */
    public static void captureBase(Entity entity, String partName, ModelPart part) {
        getState(entity, partName).base = new PoseSnapshot(part);
    }

    /**
     * Stores the current/live pose of {@code part} for the given entity and part name.
     */
    public static void captureCurrent(Entity entity, String partName, ModelPart part) {
        getState(entity, partName).current = new PoseSnapshot(part);
    }

    /**
     * Returns the stored base pose, or {@code null} if none was captured.
     */
    public static PoseSnapshot getBase(Entity entity, String partName) {
        State state = getStateOrNull(entity, partName);
        return state == null ? null : state.base;
    }

    /**
     * Returns the stored current pose, or {@code null} if none was captured.
     */
    public static PoseSnapshot getCurrent(Entity entity, String partName) {
        State state = getStateOrNull(entity, partName);
        return state == null ? null : state.current;
    }

    /**
     * Returns {@code true} if both a base and a current pose exist for the part.
     */
    public static boolean hasDelta(Entity entity, String partName) {
        State state = getStateOrNull(entity, partName);
        return state != null && state.base != null && state.current != null;
    }

    /**
     * Returns the positional delta (current - base) in model pixels.
     */
    public static Vector3f getTranslationDelta(Entity entity, String partName) {
        State state = requireState(entity, partName);
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
    public static Vector3f getRotationDelta(Entity entity, String partName) {
        State state = requireState(entity, partName);
        return new Vector3f(
                angleDelta(state.current.xRot, state.base.xRot),
                angleDelta(state.current.yRot, state.base.yRot),
                angleDelta(state.current.zRot, state.base.zRot)
        );
    }

    /**
     * Removes all tracked parts for the given entity.
     */
    public static void clear(Entity entity) {
        STATES.remove(entity);
    }

    /**
     * Removes a single tracked part for the given entity.
     */
    public static void clearPart(Entity entity, String partName) {
        Map<String, State> map = STATES.get(entity);
        if (map == null) return;
        map.remove(partName);
        if (map.isEmpty()) {
            STATES.remove(entity);
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

    private static State requireState(Entity entity, String partName) {
        return getState(entity, partName);
    }

    private static State getState(Entity entity, String partName) {
        return STATES.computeIfAbsent(entity, k -> new HashMap<>())
                .computeIfAbsent(partName, k -> new State());
    }

    private static State getStateOrNull(Entity entity, String partName) {
        Map<String, State> map = STATES.get(entity);
        return map == null ? null : map.get(partName);
    }

    private static final class State {
        PoseSnapshot base;
        PoseSnapshot current;
    }
}
