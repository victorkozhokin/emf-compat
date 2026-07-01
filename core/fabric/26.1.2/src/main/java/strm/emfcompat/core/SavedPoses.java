package strm.emfcompat.core;

import org.jetbrains.annotations.Nullable;

public record SavedPoses(
        @Nullable PoseSnapshot leftArm,
        @Nullable PoseSnapshot rightArm
) {
}
