package strm.emfcompat.core.ik;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/** Coordinate conversions and interpolation shared by one-bone IK users. */
public final class IKMath {
    private IKMath() {
    }

    /** Direction of a +y-pointing limb rotated around x and then y. */
    public static Vector3f directionXY(float x, float y) {
        float sin = (float) Math.sin(x);
        return new Vector3f(sin * (float) Math.sin(y), (float) Math.cos(x),
                sin * (float) Math.cos(y));
    }

    /** Direction of a +y-pointing limb rotated around x and then z. */
    public static Vector3f directionXZ(float x, float z) {
        float cos = (float) Math.cos(x);
        return new Vector3f(-cos * (float) Math.sin(z), cos * (float) Math.cos(z),
                (float) Math.sin(x));
    }

    /**
     * X/y rotations that point a +y-pointing limb along {x, y, z} (any length); {@code null} when
     * the way is shorter than 1e-3. The same angles {@link OneBoneIK#solveXY} gives.
     */
    public static Angles anglesXY(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (!(length >= 1e-3f)) return null;
        return new Angles(-(float) Math.acos(Math.max(-1f, Math.min(1f, y / length))),
                (float) Math.atan2(-x, -z));
    }

    /** Converts a direction to x/z rotations, avoiding the straight-up singularity of x/y. */
    public static Angles anglesXZ(Vector3f direction) {
        double cos = Math.sqrt(direction.x * direction.x + direction.y * direction.y);
        if (direction.y < 0) cos = -cos;
        float x = (float) Math.atan2(direction.z, cos);
        float z = (float) Math.atan2(-direction.x * Math.signum(cos == 0 ? 1 : cos),
                Math.abs(direction.y));
        return new Angles(x, z);
    }

    /** X/z solution constrained to the usual downward-facing hemisphere of a leg. */
    public static Angles anglesXZDownward(Vector3f direction) {
        float x = (float) Math.asin(Math.max(-1f, Math.min(1f, direction.z)));
        float z = (float) Math.atan2(-direction.x, direction.y);
        return new Angles(x, z);
    }

    /**
     * {@link #anglesXZDownward} for a way of any length; {@code null} when it is shorter than 1e-4
     * or not finite.
     */
    public static Angles anglesXZDownward(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (!Float.isFinite(length) || length < 1e-4f) return null;
        return anglesXZDownward(new Vector3f(x, y, z).div(length));
    }

    /**
     * The x, y, z angles of a rotation built as {@code rotationZYX(z, y, x)} - a model part's
     * xRot, yRot, zRot. JOML 1.10.5's getEulerAnglesZYX has +y*y in the pitch denominator and
     * misses on a yawed limb, so this is the exact inverse.
     */
    public static Vector3f eulerZYX(Quaternionfc q) {
        float w = q.w(), x = q.x(), y = q.y(), z = q.z();
        return new Vector3f(
                (float) Math.atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y)),
                (float) Math.asin(Math.max(-1f, Math.min(1f, 2 * (w * y - z * x)))),
                (float) Math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z)));
    }

    /**
     * Turn about the body's origin that brings a rigid limb of {@code limb} from {@code joint}
     * just within reach of {@code target} (both relative to that origin), clamped to
     * [{@code min}, {@code max}] radians: a negative min also folds the body towards a target
     * that is too close. Never stretches the limb.
     */
    public static Quaternionf reachTurn(Vector3f joint, Vector3f target, float limb, float min, float max) {
        float a = joint.length(), b = target.length();
        if (a < 1e-5f || b < 1e-5f) return new Quaternionf();
        float theta = (float) Math.acos(Math.max(-1f, Math.min(1f, joint.dot(target) / (a * b))));
        float desired = (float) Math.acos(Math.max(-1f, Math.min(1f, (a * a + b * b - limb * limb) / (2 * a * b))));
        float angle = Math.max(min, Math.min(max, theta - desired));
        if (angle == 0f) return new Quaternionf();
        Vector3f axis = new Vector3f(joint).cross(target);
        if (axis.lengthSquared() < 1e-8f) {
            axis.set(joint).cross(Math.abs(joint.y) < a * .9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0));
        }
        return new Quaternionf().rotationAxis(angle, axis.normalize());
    }

    /** {@code from} moved towards {@code to} by {@code weight} the short way round. */
    public static float approachAngle(float from, float to, float weight) {
        return from + wrap(to - from) * weight;
    }

    public static Vector3f slerp(Vector3f from, Vector3f to, float t) {
        if (t <= 0f) return new Vector3f(from);
        if (t >= 1f) return new Vector3f(to);
        float dot = Math.max(-1f, Math.min(1f, from.dot(to)));
        if (dot > 0.9995f) return new Vector3f(from).lerp(to, t).normalize();
        double angle = Math.acos(dot);
        double sin = Math.sin(angle);
        float a = (float) (Math.sin((1 - t) * angle) / sin);
        float b = (float) (Math.sin(t * angle) / sin);
        return new Vector3f(from).mul(a).add(new Vector3f(to).mul(b)).normalize();
    }

    /** Brings an angle into (-pi, pi]. */
    public static float wrap(float angle) {
        double twoPi = 2 * Math.PI;
        double wrapped = (angle + Math.PI) % twoPi;
        if (wrapped <= 0) wrapped += twoPi;
        return (float) (wrapped - Math.PI);
    }

    public record Angles(float x, float secondary) {
    }
}
