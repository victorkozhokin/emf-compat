package strm.emfcompat.core.ik;

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
