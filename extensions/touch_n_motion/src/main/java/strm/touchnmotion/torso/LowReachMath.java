package strm.touchnmotion.torso;

import strm.emfcompat.core.ik.IKMath;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Fixed-length arm fit: a close target needs less folding, a distant one more reach. */
final class LowReachMath {
    static Quaternionf turn(Vector3f shoulder, Vector3f target, float arm, float limit) {
        return IKMath.reachTurn(shoulder, target, arm, -limit, limit);
    }

    static Vector3f shift(Vector3f shoulder, Vector3f target, float arm, float limit) {
        Vector3f delta = new Vector3f(target).sub(shoulder);
        float error = delta.length() - arm;
        delta.y = 0;
        if (delta.lengthSquared() < 1e-6f) return new Vector3f();
        return delta.normalize().mul(Math.max(-limit, Math.min(limit, error)));
    }

}
