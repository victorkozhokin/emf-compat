package strm.emfcompat.animationadditions.gesture;

import org.joml.Vector3f;

/** Small sums the gestures share, model pixels. */
final class Reach {
    private Reach() {
    }

    /** The same point on the other side of the body. */
    static Vector3f mirror(Vector3f point) {
        return new Vector3f(-point.x, point.y, point.z);
    }

    /** How far forward the torso bends to a point at this height (the shoulder is at 2, the soles at 24), 0..1. */
    static float low(float pointY) {
        float v = Math.max(0, Math.min(1, (pointY - 6f) / 14f));
        return v * v * (3 - 2 * v);
    }
}
