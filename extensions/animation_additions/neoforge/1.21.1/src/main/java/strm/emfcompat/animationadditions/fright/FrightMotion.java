package strm.emfcompat.animationadditions.fright;

/**
 * The shape of a fright, free of the game. All three are the shudder of shaking something off -
 * hard at first and running down - with the shoulders drawn up; none of them takes the hands, a
 * fright comes in the middle of doing something.
 *
 * <ul>
 * <li>a start: the shudder, the shoulders up, and a short look to one side and the other;</li>
 * <li>a scare: the same after a small jump, the head ducked and the arms a little off the body;</li>
 * <li>a terror: all that, and a look round at the sound from under the shoulder.</li>
 * </ul>
 *
 * <p>Everything here comes on and runs out to nothing within {@link #seconds}: the pose is its own
 * way back.</p>
 */
public final class FrightMotion {

    public static final int LIGHT = 1, MEDIUM = 2, STRONG = 3;

    private static final float[] SECONDS = {0, 1.1f, 1.6f, 2.4f};
    /** Radians the torso shudders round; pixels the shoulders come up. */
    private static final float[] SHUDDER = {0, 0.06f, 0.08f, 0.1f}, SHRUG = {0, 1.0f, 1.4f, 1.7f};
    /** Radians: the look to the sides; the head's duck and the torso's; the arms forward and out from the body. Pixels: the jump. */
    private static final float[] GLANCE = {0, 0.2f, 0.12f, 0f}, DUCK = {0, 0.05f, 0.2f, 0.28f}, BOW = {0, 0.05f, 0.11f, 0.16f},
            ARMS_UP = {0, 0f, 0.2f, 0.26f}, ARMS_OUT = {0, 0f, 0.14f, 0.18f}, HOP = {0, 0f, 1.1f, 1.4f};
    /** Pixels the foot away from the sound is set back from it, and the other after it: a wary stand. */
    private static final float[] STEP = {0, 1.4f, 2.3f, 3.1f}, STEP_AFTER = {0, 0.8f, 1.3f, 1.7f}, WIDER = {0, 0.35f, 0.5f, 0.7f};
    /** Radians, in the middle of it: the arms brought a little forward, and swung to one side and the other with the look about. */
    private static final float[] ARMS_FORWARD = {0, 0.12f, 0.18f, 0.24f}, ARMS_SWAY = {0, 0.1f, 0.13f, 0.16f};
    /** Seconds: the coming on; the jump. */
    private static final float ATTACK = 0.13f, HOP_SECONDS = 0.3f;

    /**
     * What is added to the pose, all of it zero when there is no fright. {@code yaw}, {@code roll},
     * {@code bow}: the torso, radians. {@code shrug}: pixels the shoulders are up; {@code armsUp},
     * {@code armsOut}: radians each arm is off the body, forward and to its side; {@code tremble}:
     * radians the arms shake, one against the other; {@code sway}: radians both swing to a side (the right above zero). {@code glance}, {@code duck}: the head's turn
     * and drop, radians; {@code round}: how much of the way to the sound the head is turned, 0..1.
     * {@code hop}: pixels the whole body is off the ground.
     */
    public record Pose(float yaw, float roll, float bow, float shrug, float armsUp, float armsOut, float tremble, float sway,
                       float glance, float duck, float round, float hop) {
        public static final Pose NONE = new Pose(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private FrightMotion() {
    }

    /** How long a fright of this level lasts, seconds. */
    public static float seconds(int level) {
        return SECONDS[level];
    }

    /** The pose {@code t} seconds into a fright. */
    public static Pose pose(int level, float t) {
        float total = SECONDS[level];
        if (level <= 0 || t < 0f || t >= total) return Pose.NONE;
        float s = t / total;
        // On at once, held, and let go of over the second half.
        float held = smooth(t / ATTACK) * (1f - smooth((s - 0.45f) / 0.55f));
        // The shudder: quick at first and slowing, each swing less than the last.
        double turn = Math.PI * 2 * (5.0 * t - 1.0 * t * t);
        float run = smooth(t / ATTACK) * (float) Math.exp(-2.4 * s) * (1f - smooth((s - 0.7f) / 0.3f));
        float shudder = SHUDDER[level] * run;
        // One look to a side and one to the other, once the first jolt is over.
        float glance = GLANCE[level] * (float) Math.sin(Math.PI * 2 * s) * smooth(s / 0.15f) * (1f - smooth((s - 0.8f) / 0.2f));
        // Round to the sound a little after the jolt, and back before the rest is over.
        float round = level == STRONG ? smooth((t - 0.1f) / 0.3f) * (1f - smooth((s - 0.62f) / 0.3f)) : 0f;
        // The middle of it: wary, the hands a little before the body and going from side to side as the look does.
        float middle = smooth((s - 0.12f) / 0.2f) * (1f - smooth((s - 0.6f) / 0.3f));
        float sway = ARMS_SWAY[level] * (float) Math.sin(Math.PI * 2 * s) * middle;
        float hop = t < HOP_SECONDS ? HOP[level] * (float) Math.sin(Math.PI * t / HOP_SECONDS) : 0f;
        return new Pose(shudder * (float) Math.sin(turn), 0.35f * shudder * (float) Math.cos(turn), BOW[level] * held,
                SHRUG[level] * held, ARMS_UP[level] * held + ARMS_FORWARD[level] * middle, ARMS_OUT[level] * held, 0.9f * shudder * (float) Math.sin(turn - 1.1), sway,
                glance, DUCK[level] * held, round, hop);
    }

    /** Whether the feet are in the wary stand {@code t} seconds in: from just after the jolt until the fright is running out - then they step home. */
    public static boolean wary(int level, float t) {
        return level > 0 && t > 0.06f && t < SECONDS[level] * 0.72f;
    }

    /**
     * Where a sole stands in the wary stand, model pixels from where the pack has it (the right is
     * -x, forward -z). {@code sx}, {@code sz}: the level direction to the sound, a unit vector, or
     * zeros for one right here - taken to be ahead. Both feet go back from the sound, the one on the side away from it
     * furthest; dead ahead or behind, the right one.
     */
    public static org.joml.Vector3f foot(int level, boolean right, float sx, float sz) {
        if (sx * sx + sz * sz < 1e-4f) {
            sx = 0f;
            sz = -1f;
        }
        boolean rightLeads = -sx <= 0f;
        float far = right == rightLeads ? STEP[level] : STEP_AFTER[level];
        org.joml.Vector3f foot = new org.joml.Vector3f(-sx * far, 0f, -sz * far);
        // Both feet move: the stand is a little wider too, the worse the fright.
        foot.x += right ? -WIDER[level] : WIDER[level];
        return foot;
    }

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }
}
