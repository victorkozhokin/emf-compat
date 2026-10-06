package strm.emfcompat.animationadditions.buttonpress;

import org.joml.Vector3f;

/** A light switch needs only a small weight shift, without delaying the action. */
final class LeverEffort {
    static Vector3f shift(Vector3f grip, boolean pressing) {
        Vector3f direction = new Vector3f(grip.x, 0, grip.z);
        if (direction.lengthSquared() < 1e-5f) return new Vector3f();
        return direction.normalize().mul(pressing ? .55f : .12f);
    }
    static Vector3f step(Vector3f grip) {
        return shift(grip, true).mul(.9f / .55f);
    }
    static float ease(float t) { return t * t * (3 - 2 * t); }
    static float lift(float t) { float s = (float) Math.sin(Math.PI * t); return s * s; }
}

