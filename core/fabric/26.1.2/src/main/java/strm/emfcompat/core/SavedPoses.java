package strm.emfcompat.core;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Immutable holder for captured model-part poses.
 *
 * <p>The legacy two-argument constructor is kept for compatibility with existing
 * arm-only callers. New callers can also supply a per-part pose map to capture
 * full-body poses (head, body, legs, etc.).</p>
 */
public record SavedPoses(
        @Nullable PoseSnapshot leftArm,
        @Nullable PoseSnapshot rightArm,
        @Nullable Map<String, PoseSnapshot> parts
) {
    public SavedPoses(@Nullable PoseSnapshot leftArm, @Nullable PoseSnapshot rightArm) {
        this(leftArm, rightArm, null);
    }
}
