package strm.emfcompat.core;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Immutable holder for captured model-part poses.
 *
 * <p>The legacy constructors are kept for compatibility with existing arm-only and
 * part-map callers. New callers can also supply a per-part pose map to capture full-body
 * poses (head, body, legs, etc.).</p>
 *
 * <p>{@code bodyBase} is the body part's position at capture time (before EMF re-animates
 * the model). When non-null, the arm slots are restored <em>following the body</em>: their
 * rotation is applied absolutely while their position is offset by how far EMF has since
 * moved the body ({@code currentBody - bodyBase}). This keeps a captured arm pose attached
 * to the torso during movement and crouching. When null, the arm slots are restored
 * rotation-only, as before.</p>
 */
public record SavedPoses(
        @Nullable PoseSnapshot leftArm,
        @Nullable PoseSnapshot rightArm,
        @Nullable Map<String, PoseSnapshot> parts,
        @Nullable Vector3f bodyBase
) {
    public SavedPoses(@Nullable PoseSnapshot leftArm, @Nullable PoseSnapshot rightArm, @Nullable Map<String, PoseSnapshot> parts) {
        this(leftArm, rightArm, parts, null);
    }

    public SavedPoses(@Nullable PoseSnapshot leftArm, @Nullable PoseSnapshot rightArm) {
        this(leftArm, rightArm, null, null);
    }
}
