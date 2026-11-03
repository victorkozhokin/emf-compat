package strm.touchnmotion.transport;

import org.joml.Vector3f;

/** Bounded contact, effort and stance envelopes; model +Z points backwards. */
public final class BraceMath {
    private BraceMath() {}
    static boolean reachable(Vector3f target, Vector3f shoulder, boolean right, boolean retained) {
        return within(target, shoulder, right, retained ? 14.5f : 12.8f, 1);
    }

    /** No nearer than 6 px to the shoulder nor further than {@code far}, at most {@code across} px over the body's middle. */
    static boolean within(Vector3f target, Vector3f shoulder, boolean right, float far, float across) {
        float d = target.distance(shoulder);
        return d >= 6 && d <= far && (right ? target.x <= across : target.x >= -across);
    }
    static float load(float acceleration) { return Math.min(1, Math.max(0, acceleration) / 6); }
    static Vector3f foot(boolean right, Vector3f force, float load) {
        float side = right ? -1 : 1;
        return new Vector3f(side * (.65f + .65f * load) + force.x * .35f, 0,
                side * (.4f + .7f * load) * force.z);
    }
}
