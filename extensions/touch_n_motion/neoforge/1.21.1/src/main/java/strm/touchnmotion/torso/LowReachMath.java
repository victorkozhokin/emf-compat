package strm.touchnmotion.torso;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Fixed-length arm fit: a close target needs less folding, a distant one more reach. */
final class LowReachMath {
    static Quaternionf turn(Vector3f shoulder, Vector3f target, float arm, float limit) {
        float a = shoulder.length(), b = target.length();
        if (a < 1e-5f || b < 1e-5f) return new Quaternionf();
        float theta = (float) Math.acos(clamp(shoulder.dot(target) / (a * b)));
        float desired = (float) Math.acos(clamp((a * a + b * b - arm * arm) / (2 * a * b)));
        float angle = Math.max(-limit, Math.min(limit, theta - desired));
        Vector3f axis = new Vector3f(shoulder).cross(target);
        if (axis.lengthSquared() < 1e-8f) {
            axis.set(shoulder).cross(Math.abs(shoulder.y) < a * .9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0));
        }
        return new Quaternionf().rotationAxis(angle, axis.normalize());
    }

    static Vector3f shift(Vector3f shoulder, Vector3f target, float arm, float limit) {
        Vector3f delta = new Vector3f(target).sub(shoulder);
        float error = delta.length() - arm;
        delta.y = 0;
        if (delta.lengthSquared() < 1e-6f) return new Vector3f();
        return delta.normalize().mul(Math.max(-limit, Math.min(limit, error)));
    }

    private static float clamp(float v) { return Math.max(-1, Math.min(1, v)); }
}
