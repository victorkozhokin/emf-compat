package strm.emfcompat.animationadditions.transport;

import org.joml.Vector3d;
import strm.emfcompat.animationadditions.interaction.Ease;

/** Rope-specific style envelopes and a double-precision segment projection. */
public final class RopePoseMath {
    private RopePoseMath() {}
    public static double fraction(Vector3d a, Vector3d b, Vector3d point) {
        Vector3d segment = new Vector3d(b).sub(a);
        double length = segment.lengthSquared();
        return length < 1e-12 ? 0 : Ease.unit(new Vector3d(point).sub(a).dot(segment) / length);
    }
    public static boolean reachable(org.joml.Vector3f target, org.joml.Vector3f shoulder, boolean right) {
        float distance = target.distance(shoulder);
        // Both hands may hold one side cord at separate heights without crossing forearms.
        return distance >= 6 && distance <= 14.5f && (right ? target.x <= 6 : target.x >= -6);
    }
    public static double slide(double from, double to, double dt) {
        return from + (to - from) * strm.emfcompat.animationadditions.interaction.Smoothing.follow(dt, .18);
    }
    public static Vector3d sample(int count, double coordinate, java.util.function.IntFunction<Vector3d> point) {
        coordinate = Math.max(0, Math.min(count - 1, coordinate));
        int i = Math.min(count - 2, (int) coordinate);
        return point.apply(i).lerp(point.apply(i + 1), coordinate - i);
    }
    static float gain(double speed) { return 1 + .65f * (float) Ease.unit(speed / 5); }
    static org.joml.Vector3f foot(boolean right) {
        return new org.joml.Vector3f(right ? -.75f : .75f, 0, .9f);
    }
    static float edgeLift(boolean enabled, boolean planted, boolean edge, boolean crouching, boolean helper) {
        return enabled && planted && edge && !helper ? lift(crouching, false) : 0;
    }
    static float lift(boolean crouching, boolean helper) { return (crouching ? .7f : 1.8f) * (helper ? .35f : 1); }
}
