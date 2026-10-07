package strm.emfcompat.animationadditions.fright;

import org.joml.Vector3f;

/**
 * The shape of a fright, free of the game: back from the sound. It takes no hands - a fright comes
 * in the middle of doing something, and is laid over it.
 *
 * <p>Three things move together. The <b>feet</b> step back from the sound, the one on the side
 * away from it first and furthest, and step home again. The <b>body</b> runs on the feet: how far
 * it is back, how far it leans away and how far the head is round to the sound is the share of
 * the way the soles have come ({@code stand}, 0..1), so it is never ahead of them; and with each
 * step the chest turns a little against the hips ({@code swing}). The <b>arms</b> move once: they
 * jerk out from the body, one a beat before the other, and are then held there on guard - a little
 * out and before the body, still - until the feet go home, and come down with them. Over that,
 * on the clock, the <b>shake</b> of shaking something off, smaller and slower, in the torso and
 * the head - and a small hop for the worse ones.</p>
 *
 * <p>The shoulders are not drawn up, and there is only a trace of trembling: both were tried, and
 * made the pose cramped and the fright unconvincing.</p>
 */
public final class FrightMotion {

    public static final int LIGHT = 1, MEDIUM = 2, STRONG = 3;

    /**
     * A start, a scare, a terror - the same fright, each more of it: the step back longer, the
     * lean away and the guard a little more, the look at the sound further round. A scare and a
     * terror begin with a small hop that lands in the stand.
     */
    private static final float[] LEAD = {0, 1.9f, 2.7f, 3.4f}, AFTER = {0, 0.9f, 1.4f, 1.9f}, WIDER = {0, 0.25f, 0.4f, 0.55f};
    /** Radians: the lean away; the chest against the hips with a step; the arms' guard, out and before the body. The head's share of the way round to the sound. Pixels: the hop. */
    private static final float[] LEAN = {0, 0.045f, 0.065f, 0.085f}, CHEST_SWING = {0, 0.07f, 0.09f, 0.11f},
            GUARD_OUT = {0, 0.2f, 0.26f, 0.31f}, GUARD_UP = {0, 0.14f, 0.18f, 0.22f},
            ROUND = {0, 0.45f, 0.7f, 0.9f}, HOP = {0, 0f, 1.0f, 1.4f}, TREMBLING = {0, 1f, 1.3f, 1.6f};
    /** Radians the body gives as the hop lands. */
    private static final float[] LANDING = {0, 0f, 0.04f, 0.06f};
    /** Seconds the wary stand is kept before the feet go home. */
    private static final float[] KEPT = {0, 1.0f, 1.5f, 2.1f};
    /** Seconds the shake runs; how much of the shake-off's it is. */
    private static final float[] SHAKE_SECONDS = {0, 1.0f, 1.3f, 1.6f}, SHAKE = {0, 0.42f, 0.6f, 0.8f};
    /** The trace of trembling: turns a second, and radians of it in the arms. */
    private static final float TREMBLE_RATE = 11f, TREMBLE = 0.006f;
    private static final float HOP_SECONDS = 0.3f;
    /** Before the hop, seconds: the body gathers for it - bent forward, the arms drawn back a little. Radians of that bend and of the arms. */
    private static final float GATHER_SECONDS = 0.16f;
    /** Pixels the body drops as it gathers: the small crouch the hop is sprung from. */
    private static final float[] CROUCH = {0, 0f, 1.0f, 1.4f};
    private static final float[] GATHER = {0, 0f, 0.1f, 0.14f}, GATHER_ARMS = {0, 0f, 0.1f, 0.14f};

    /**
     * What is added to the pose. {@code yaw}, {@code roll}: the torso's turn and tilt, radians;
     * {@code bow}: its lean, forward above zero. {@code armsUp}, {@code armsOut}: radians each arm
     * is off the body, forward and to its side; {@code against}: the right arm back and the left
     * forward by this (the other way below zero); {@code sway}: both to a side (the right above
     * zero). {@code nod}: the head's turn with the shake; {@code round}: how much of the way to the
     * sound it is turned, 0..1. {@code hop}: pixels the whole body is off the ground; {@code crouch}: pixels everything above the legs is down, gathering for it.
     */
    public record Pose(float yaw, float roll, float bow, float armsUp, float armsOut, float against, float sway,
                       float nod, float round, float hop, float crouch) {
        public static final Pose NONE = new Pose(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** The share of the feet's move the body goes with. */
    public static final float CARRY = 0.75f;

    private FrightMotion() {
    }

    /** How long the wary stand is kept, seconds, before the feet are sent home. */
    public static float kept(int level) {
        return KEPT[level];
    }

    /** How long the shake runs on the clock, seconds: nothing of it is left after. */
    public static float shake(int level) {
        return SHAKE_SECONDS[level];
    }

    /** How long the body gathers itself before the hop, seconds; zero for a start. */
    public static float gather(int level) {
        return level == LIGHT ? 0f : GATHER_SECONDS;
    }

    /** How long the hop lasts, seconds, after the gathering - the feet are put in their stand in the air; zero for a start, which steps. */
    public static float hop(int level) {
        return level == LIGHT ? 0f : HOP_SECONDS;
    }

    /**
     * Where a sole stands in the wary stand, model pixels from where the pack has it (the right is
     * -x, forward -z). {@code sx}, {@code sz}: the level direction to the sound, a unit vector, or
     * zeros for one right here - taken to be ahead. Both feet go back, the one on the side away
     * from the sound furthest; dead ahead or behind, the right one.
     */
    public static Vector3f foot(int level, boolean right, float sx, float sz) {
        if (sx * sx + sz * sz < 1e-4f) {
            sx = 0f;
            sz = -1f;
        }
        boolean leads = right == (-sx <= 0f);
        float back = leads ? LEAD[level] : AFTER[level];
        return new Vector3f(-sx * back + (right ? -WIDER[level] : WIDER[level]), 0f, -sz * back);
    }

    /**
     * The pose {@code t} seconds in. {@code stand}: the share of the way the soles are to their
     * stand, 0..1. {@code swing}: the step under way - above zero the right foot's, below the
     * left's, as high as the foot is in its arc. {@code sz}: the forward part of the direction to
     * the sound (-1 dead ahead, 1 behind).
     */
    public static Pose pose(int level, float t, float stand, float swing, float sz) {
        if (level <= 0 || t < 0f) return Pose.NONE;
        stand = Math.max(0f, Math.min(1f, stand));
        // The shake: coming on softly, a few slow swings each less than the last, a wave through the arms, the torso, the head last.
        float s = t / SHAKE_SECONDS[level], k = SHAKE[level];
        float in = s >= 1f ? 0f : smooth(s / 0.22f) * (1f - smooth((s - 0.3f) / 0.7f));
        double turn = Math.PI * 2 * (3.4 * s - 0.9 * s * s);
        float arms = (float) Math.sin(turn) * in, wave = (float) Math.sin(turn - 1.1) * in;
        float torso = (float) Math.sin(turn - 0.7) * in, head = (float) Math.sin(turn - 1.5) * in;
        float fine = TREMBLE * TREMBLING[level] * (float) Math.sin(Math.PI * 2 * TREMBLE_RATE * t)
                * smooth(t / 0.15f) * (1f - smooth((t - KEPT[level] * 0.4f) / (KEPT[level] * 0.6f)));
        float hopSeconds = hop(level);
        // Gathered first - bent forward, the arms back - and let go of into the hop.
        float before = gather(level), air = t - before;
        float gathered = before <= 0f ? 0f : smooth(t / before) * (1f - smooth(air / (hopSeconds * 0.6f)));
        // The crouch is let go of quicker than the bend: it is what throws the body up.
        float crouched = before <= 0f ? 0f : smooth(t / before) * (1f - smooth(air / (hopSeconds * 0.3f)));
        float hop = hopSeconds > 0f && air > 0f && air < hopSeconds ? HOP[level] * (float) Math.sin(Math.PI * air / hopSeconds) : 0f;
        // The give of the landing: just as the feet come down, eased off over half a second.
        float landing = hopSeconds <= 0f ? 0f : smooth((air - hopSeconds * 0.7f) / (hopSeconds * 0.5f)) * (1f - smooth((air - hopSeconds - 0.1f) / 0.5f));
        // The arms: out at once with the first jerk, and kept there by the stand - the clock holds them until the feet have got there.
        float guard = Math.max(smooth(t / 0.18f) * (1f - smooth((t - 0.45f) / 0.4f)), stand);
        // One move only: the first swing of the shake, one arm a beat before the other, and then still.
        float first = 1f - smooth((s - 0.1f) / 0.16f);
        // With a step the chest turns against the hips: the right foot going back takes the right hip back, and the right shoulder comes forward.
        return new Pose(0.15f * k * torso - CHEST_SWING[level] * swing, 0.05f * k * (float) Math.cos(turn - 0.7) * in,
                // Upright and leaning away from it: back from a sound ahead, forward from one behind.
                0.04f * k * in + LEAN[level] * sz * stand + LANDING[level] * landing + GATHER[level] * gathered,
                GUARD_UP[level] * guard - GATHER_ARMS[level] * gathered, GUARD_OUT[level] * guard,
                0.24f * k * arms * first + fine, 0.1f * k * wave * first,
                0.08f * k * head, ROUND[level] * stand, hop, CROUCH[level] * crouched);
    }

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }
}
