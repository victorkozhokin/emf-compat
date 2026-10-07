package strm.emfcompat.animationadditions.fishing;

/**
 * The shape of fishing, as numbers: what the rod arm, the free arm, the torso and the hips are
 * asked for at a moment of the cast, the wait, the bite and the haul. No game in here - only time
 * in, pose out.
 *
 * <p>The rod arm's pitch is the arm's own: 0 hangs, -pi/2 is level before the body, past that it
 * is up and back over the shoulder. "In" is the arm turned towards the body's middle. The torso's
 * pitch is forward when positive; its turn takes the rod's shoulder back when positive; its roll
 * drops the rod's shoulder when positive. The hips go back when positive, model pixels. The free
 * arm's pitch is back when positive, and "out" is away from the body.</p>
 *
 * <p>Nothing here moves at an even pace: a move is gathered slowly and let go fast, runs past its
 * end and comes back to it ({@link #spring}), and the body goes first - the arm is a beat behind
 * the torso in the throw and the heave. The torso is not posed apart from the arm either: on top
 * of what a phase asks, it is carried after the rod hand ({@link #follow}).</p>
 */
public final class FishingMotion {

    /** Seconds: the cast from its first move to the wait; the haul after a bite, and without one. */
    public static final float CAST = 0.5f, HAUL = 0.62f, LIFT = 0.45f;
    private static final float WIND = 0.13f, THROW = 0.24f, ARM_LATE = 0.03f;

    public record Pose(float armPitch, float armIn, float torsoPitch, float torsoTurn, float torsoRoll, float hips, float helper,
                       float freePitch, float freeOut) {
        Pose mix(Pose to, float t) {
            return new Pose(lerp(armPitch, to.armPitch, t), lerp(armIn, to.armIn, t), lerp(torsoPitch, to.torsoPitch, t),
                    lerp(torsoTurn, to.torsoTurn, t), lerp(torsoRoll, to.torsoRoll, t), lerp(hips, to.hips, t), lerp(helper, to.helper, t),
                    lerp(freePitch, to.freePitch, t), lerp(freeOut, to.freeOut, t));
        }

        /** This pose with the rod arm taken from {@code arm}: the arm on its own clock. */
        Pose withArm(Pose arm) {
            return new Pose(arm.armPitch, arm.armIn, torsoPitch, torsoTurn, torsoRoll, hips, helper, freePitch, freeOut);
        }
    }

    /** The rod held out over the water, the weight on the back foot, the free arm a little off the body. */
    static final Pose WAIT = new Pose(-1.0f, 0.15f, -0.03f, 0f, 0f, 0.6f, 0f, 0.22f, 0.5f);
    /** Wound up: the rod back over the shoulder, the body back and turned after it, the free arm out before for balance. */
    private static final Pose WOUND = new Pose(-2.85f, -0.05f, -0.2f, 0.34f, -0.08f, 1.5f, 0f, -0.75f, 0.35f);
    /** Thrown: the rod whipped down before the body, the weight gone after it, the free arm flung back. */
    private static final Pose THROWN = new Pose(-0.6f, 0.25f, 0.24f, -0.2f, 0.1f, -1.8f, 0f, 0.7f, 0.75f);
    /** A bite: the rod lower and before the middle, both hands on it, the body over the front foot. */
    private static final Pose BITE = new Pose(-1.12f, 0.45f, 0.12f, 0f, 0.03f, -1.0f, 1f, 0f, 0.2f);
    /** Gathered for the heave: down and forward for an instant. */
    private static final Pose GATHERED = new Pose(-0.9f, 0.45f, 0.2f, -0.05f, 0.05f, -1.5f, 1f, 0f, 0.2f);
    /** Hauled: the rod heaved up and back with both hands, the body thrown back. */
    private static final Pose HAULED = new Pose(-2.35f, 0.35f, -0.26f, 0.14f, -0.06f, 1.9f, 1f, 0f, 0.2f);
    /** Lifted: the rod raised in one hand, with nothing on it, the free arm out. */
    private static final Pose LIFTED = new Pose(-1.95f, 0.1f, -0.1f, 0.05f, -0.03f, 0.9f, 0f, 0.3f, 0.7f);

    private FishingMotion() {
    }

    /** {@code seconds} into the cast: wound up, thrown, rung down into the wait. */
    public static Pose cast(float seconds) {
        return body(seconds).withArm(body(seconds - ARM_LATE));
    }

    private static Pose body(float seconds) {
        // Gathered slowing, let go quickening, and past the wait's pose and back to it.
        if (seconds < WIND) return WAIT.mix(WOUND, out(seconds / WIND));
        if (seconds < THROW) return WOUND.mix(THROWN, in((seconds - WIND) / (THROW - WIND)));
        return THROWN.mix(WAIT, spring((seconds - THROW) / (CAST - THROW)));
    }

    /** Waiting, {@code seconds} into it: breath, the tip riding, the weight drifting from foot to foot. */
    public static Pose waiting(float seconds) {
        float breath = (float) Math.sin(seconds * 1.7), drift = (float) Math.sin(seconds * 0.45 + 1), tip = (float) Math.sin(seconds * 2.9 + 0.5);
        return new Pose(WAIT.armPitch + 0.035f * breath + 0.02f * tip, WAIT.armIn + 0.03f * drift, WAIT.torsoPitch + 0.015f * breath,
                0.03f * drift, 0.012f * drift, WAIT.hips + 0.5f * drift, 0f, WAIT.freePitch + 0.04f * breath, WAIT.freeOut + 0.04f * drift);
    }

    /** {@code seconds} into a bite: the tip snatched down with the float, then worked against the pull. */
    public static Pose bite(float seconds) {
        float snatch = 0.38f * (float) Math.exp(-seconds * 7);
        // The fish on the line: quick tugs dying away over a slower pull.
        float tug = (0.07f * (float) Math.sin(seconds * 31) * (float) Math.exp(-seconds * 2.2) + 0.05f * (float) Math.sin(seconds * 9.5));
        float on = out(seconds / 0.12f);
        return new Pose(BITE.armPitch + snatch + tug, lerp(WAIT.armIn, BITE.armIn, on), lerp(WAIT.torsoPitch, BITE.torsoPitch, on) + snatch * 0.5f + tug * 0.3f,
                tug * 0.4f, BITE.torsoRoll + tug * 0.2f, lerp(WAIT.hips, BITE.hips, on) - snatch * 2f, on, 0f, 0.2f);
    }

    /** {@code seconds} into bringing the line in - a haul after a bite ({@code hooked}), a lift without - from the pose it began in. */
    public static Pose haul(Pose from, float seconds, boolean hooked) {
        float length = hooked ? HAUL : LIFT;
        Pose pose;
        if (hooked) {
            // Down for an instant, then up with the whole body - the arm a beat behind it - and past the top and back.
            float gather = 0.07f;
            Pose trunk = seconds < gather ? from.mix(GATHERED, out(seconds / gather)) : GATHERED.mix(HAULED, spring((seconds - gather) / (length * 0.5f)));
            float late = seconds - ARM_LATE;
            Pose arm = late < gather ? from.mix(GATHERED, out(late / gather)) : GATHERED.mix(HAULED, spring((late - gather) / (length * 0.5f)));
            pose = trunk.withArm(arm);
        } else {
            pose = from.mix(LIFTED, spring(seconds / (length * 0.6f)));
        }
        // The second hand lets go as the rod comes to rest; the rest is given back by the hands' own fade.
        float held = seconds < length * 0.65f ? 1f : Math.max(0f, 1f - (seconds - length * 0.65f) / (length * 0.2f));
        return new Pose(pose.armPitch, pose.armIn, pose.torsoPitch, pose.torsoTurn, pose.torsoRoll, pose.hips, pose.helper * held,
                pose.freePitch, pose.freeOut);
    }

    /**
     * The torso carried after the rod hand, on top of what the phase asks: {pitch, turn, roll} to
     * add. Where the fist is against its shoulder says it - reached out before the body the chest
     * goes forward and that shoulder with it; drawn back over the shoulder they go back; raised,
     * the shoulder comes up. Both hands on the rod, the chest squares up to it.
     */
    public static float[] follow(float armPitch, float armIn, float helper) {
        // The fist against the shoulder for an arm of length 1: before the body and below it.
        float before = -(float) Math.sin(armPitch), below = (float) Math.cos(armPitch);
        float pitch = 0.09f * before * Math.max(0f, below + 0.4f) - 0.1f * Math.max(0f, -below) * (before < 0 ? 1.4f : 0.6f);
        float turn = -0.16f * before + 0.2f * armIn * (1f - helper);
        float roll = -0.07f * Math.max(0f, -below) + 0.04f * Math.max(0f, before) * Math.max(0f, below);
        return new float[]{pitch, turn * (1f - 0.6f * helper), roll};
    }

    /** Eased at the end: quick away, slowing in. */
    static float out(float t) {
        t = clamp(t);
        return 1f - (1f - t) * (1f - t);
    }

    /** Eased at the start: slow away, quickening. */
    static float in(float t) {
        t = clamp(t);
        return t * t * t;
    }

    /** To the end, past it and back: 0 at 0, 1 by 1, with one overshoot of about a seventh on the way. */
    static float spring(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1.6f) return 1f;
        return 1f - (float) (Math.exp(-5.2 * t) * Math.cos(7.5 * t));
    }

    private static float clamp(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
