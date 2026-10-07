package strm.emfcompat.animationadditions.fishing;

/**
 * The shape of fishing, as numbers: what the rod arm, the torso and the hips are asked for at a
 * moment of the cast, the wait, the bite and the haul. No game in here - only time in, pose out.
 *
 * <p>The rod arm's pitch is the arm's own: 0 hangs, -pi/2 is level before the body, past that it
 * is up and back over the shoulder. "In" is the arm turned towards the body's middle. The torso's
 * pitch is forward when positive; its turn takes the rod's shoulder back when positive. The hips
 * go back when positive, model pixels.</p>
 */
public final class FishingMotion {

    /** Seconds: the cast from its first move to the wait; the haul after a bite, and without one. */
    public static final float CAST = 0.62f, HAUL = 0.75f, LIFT = 0.55f;
    private static final float WIND = 0.2f, THROW = 0.36f;

    public record Pose(float armPitch, float armIn, float torsoPitch, float torsoTurn, float hips, float helper) {
        Pose mix(Pose to, float t) {
            return new Pose(lerp(armPitch, to.armPitch, t), lerp(armIn, to.armIn, t), lerp(torsoPitch, to.torsoPitch, t),
                    lerp(torsoTurn, to.torsoTurn, t), lerp(hips, to.hips, t), lerp(helper, to.helper, t));
        }
    }

    /** The rod held out over the water, the weight on the back foot. */
    static final Pose WAIT = new Pose(-1.0f, 0.15f, -0.04f, 0f, 0.6f, 0f);
    /** Wound up: the rod back over the shoulder, the body back and turned after it. */
    private static final Pose WOUND = new Pose(-2.75f, 0.05f, -0.16f, 0.3f, 1.2f, 0f);
    /** Thrown: the rod whipped down before the body, the weight gone forward after it. */
    private static final Pose THROWN = new Pose(-0.72f, 0.2f, 0.2f, -0.14f, -1.4f, 0f);
    /** A bite: the rod lower and before the middle, both hands on it, the body over the front foot. */
    private static final Pose BITE = new Pose(-1.1f, 0.45f, 0.12f, 0f, -1.0f, 1f);
    /** Hauled: the rod heaved up and back with both hands, the body thrown back. */
    private static final Pose HAULED = new Pose(-2.3f, 0.35f, -0.22f, 0.1f, 1.6f, 1f);
    /** Lifted: the rod raised in one hand, with nothing on it. */
    private static final Pose LIFTED = new Pose(-1.95f, 0.15f, -0.08f, 0f, 0.8f, 0f);

    private FishingMotion() {
    }

    /** {@code seconds} into the cast: wound up, thrown, settled into the wait. */
    public static Pose cast(float seconds) {
        if (seconds < WIND) return WAIT.mix(WOUND, smooth(seconds / WIND));
        if (seconds < THROW) return WOUND.mix(THROWN, smooth((seconds - WIND) / (THROW - WIND)));
        return THROWN.mix(WAIT, smooth((seconds - THROW) / (CAST - THROW)));
    }

    /** Waiting, {@code seconds} into it: the rod's tip rides a little. */
    public static Pose waiting(float seconds) {
        return new Pose(WAIT.armPitch + 0.03f * (float) Math.sin(seconds * 1.3), WAIT.armIn, WAIT.torsoPitch, 0f, WAIT.hips, 0f);
    }

    /** {@code seconds} into a bite: the tip is snatched down with the float and comes back to the hold. */
    public static Pose bite(float seconds) {
        float snatch = 0.28f * (float) Math.exp(-seconds * 8);
        return new Pose(BITE.armPitch + snatch, BITE.armIn, BITE.torsoPitch + snatch * 0.4f, 0f, BITE.hips, 1f);
    }

    /** {@code seconds} into bringing the line in - a haul after a bite ({@code hooked}), a lift without - from the pose it began in. */
    public static Pose haul(Pose from, float seconds, boolean hooked) {
        float length = hooked ? HAUL : LIFT;
        Pose top = hooked ? HAULED : LIFTED;
        float up = smooth(seconds / (length * 0.35f));
        Pose pose = from.mix(top, up);
        // The second hand lets go as the rod comes to rest; the rest is given back by the hands' own fade.
        float held = seconds < length * 0.6f ? 1f : Math.max(0f, 1f - (seconds - length * 0.6f) / (length * 0.2f));
        return new Pose(pose.armPitch, pose.armIn, pose.torsoPitch, pose.torsoTurn, pose.hips, pose.helper * held);
    }

    static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
