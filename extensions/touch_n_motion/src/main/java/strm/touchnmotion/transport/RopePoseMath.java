package strm.touchnmotion.transport;

import org.joml.Vector3d;
import strm.touchnmotion.interaction.Ease;

/** Rope-specific style envelopes and a double-precision segment projection. */
public final class RopePoseMath {
    private RopePoseMath() {}
    public static double fraction(Vector3d a, Vector3d b, Vector3d point) {
        Vector3d segment = new Vector3d(b).sub(a);
        double length = segment.lengthSquared();
        return length < 1e-12 ? 0 : Ease.unit(new Vector3d(point).sub(a).dot(segment) / length);
    }
    public static boolean reachable(org.joml.Vector3f target, org.joml.Vector3f shoulder, boolean right) {
        // Both hands may hold one side cord at separate heights without crossing forearms.
        return BraceMath.within(target, shoulder, right, 14.5f, 6);
    }
    /** A rope is taken only beside the body: not straight ahead nor behind, degrees from ahead... */
    static final double BESIDE_FROM = 35, BESIDE_TO = 145;
    /** ...and one already held is let go a little further round, so a small turn does not drop it. */
    static final double HELD_FROM = 25, HELD_TO = 155;

    /**
     * Whether a rope point is beside the body, model pixels (ahead is -z, to the right -x): a rope
     * in front of the player or at their back is not reached for.
     */
    public static boolean beside(org.joml.Vector3f target, boolean held) {
        double round = Math.abs(Math.toDegrees(Math.atan2(-target.x, -target.z)));
        return held ? round >= HELD_FROM && round <= HELD_TO : round >= BESIDE_FROM && round <= BESIDE_TO;
    }
    public static double slide(double from, double to, double dt) {
        return from + (to - from) * strm.touchnmotion.interaction.Smoothing.follow(dt, .18);
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
