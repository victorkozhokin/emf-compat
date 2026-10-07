package strm.emfcompat.animationadditions.fright;

import org.joml.Vector3f;

/**
 * The shape of a fright, free of the game. None of them takes the hands: a fright comes in the
 * middle of doing something, and is laid over it.
 *
 * <p>Each is two things. The <b>jolt</b> runs on the clock and is over in half a second: a shudder
 * of the torso, the shoulders jerked up and let most of the way down again, a hop. The <b>wary
 * stand</b> after it runs on the feet: how far the body is back, bowed, looking about is the share
 * of the way the soles have come to where they are set ({@code stand}, 0..1) - so the feet lead
 * and the body is never ahead of them, on the way in and on the way home alike.</p>
 *
 * <p>Three ways of taking it, to choose between:</p>
 * <ul>
 * <li>{@link #RECOIL}: back from the sound - a step back, the other foot after it, the body
 * upright and leaning away, the eyes on where it came from;</li>
 * <li>{@link #JUMP}: a hop on the spot, landing a little wider, then a look to one side and the
 * other with the hands a little before the body;</li>
 * <li>{@link #FREEZE}: stock still - the shoulders up, a small duck, and one slow look at the sound.</li>
 * </ul>
 */
public final class FrightMotion {

    public static final int LIGHT = 1, MEDIUM = 2, STRONG = 3;
    public static final int RECOIL = 1, JUMP = 2, FREEZE = 3;

    /** How much more of everything a scare and a terror are than a start. */
    private static final float[] MORE = {0, 1f, 1.45f, 1.9f};
    /** Seconds the wary stand is kept before the feet go home. */
    private static final float[] KEPT = {0, 0.9f, 1.4f, 2.0f};
    /** Seconds: the jolt's coming on; the hop; one look to a side and back. */
    private static final float ATTACK = 0.1f, HOP_SECONDS = 0.3f, LOOK_SECONDS = 1.1f;

    /**
     * What is added to the pose. {@code yaw}, {@code roll}: the torso's shudder, radians; {@code bow}:
     * its lean, forward above zero. {@code shrug}: pixels the shoulders are up. {@code armsUp},
     * {@code armsOut}: radians each arm is off the body, forward and to its side; {@code tremble}:
     * the arms shaking one against the other; {@code sway}: both to a side (the right above zero).
     * {@code glance}, {@code duck}: the head's turn and drop; {@code round}: how much of the way to
     * the sound it is turned, 0..1. {@code hop}: pixels the whole body is off the ground.
     * {@code carry}: the share of the feet's move the body goes with.
     */
    public record Pose(float yaw, float roll, float bow, float shrug, float armsUp, float armsOut, float tremble, float sway,
                       float glance, float duck, float round, float hop, float carry) {
        public static final Pose NONE = new Pose(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private FrightMotion() {
    }

    /** How long the wary stand is kept, seconds, before the feet are sent home. */
    public static float kept(int level) {
        return KEPT[level];
    }

    /** How long the jolt runs on the clock, seconds: nothing of it is left after. */
    public static float jolt(int level) {
        return 0.55f + 0.1f * level;
    }

    /** Whether the feet are put in their stand in the air, by a hop, and not by steps. */
    public static boolean lands(int variant, int level) {
        return variant == JUMP || level >= MEDIUM && variant == RECOIL;
    }

    /** How long the hop lasts, seconds; zero when there is none. */
    public static float hop(int variant, int level) {
        return hopHeight(variant, level) > 0f ? HOP_SECONDS : 0f;
    }

    private static float hopHeight(int variant, int level) {
        if (variant == JUMP) return 0.6f + 0.4f * level;
        // A start is too little to leave the ground for, but in the hop on the spot.
        return level == LIGHT ? 0f : variant == RECOIL ? 0.5f * level : 0.35f * level;
    }

    /**
     * Where a sole stands in the wary stand, model pixels from where the pack has it (the right is
     * -x, forward -z). {@code sx}, {@code sz}: the level direction to the sound, a unit vector, or
     * zeros for one right here - taken to be ahead.
     */
    public static Vector3f foot(int variant, int level, boolean right, float sx, float sz) {
        if (sx * sx + sz * sz < 1e-4f) {
            sx = 0f;
            sz = -1f;
        }
        float more = MORE[level];
        float side = right ? -1f : 1f;
        switch (variant) {
            case RECOIL -> {
                // Both back from the sound, the one on the side away from it furthest; dead ahead or behind, the right one.
                boolean leads = right == (-sx <= 0f);
                float back = (leads ? 1.9f : 0.9f) * more;
                return new Vector3f(-sx * back + side * 0.25f * more, 0f, -sz * back);
            }
            case JUMP -> {
                // Wider, and a little back.
                return new Vector3f(side * 0.75f * more - sx * 0.4f * more, 0f, -sz * 0.4f * more);
            }
            default -> {
                // Stock still: a start does not move them; worse, the far foot is set a little back.
                if (level == LIGHT) return new Vector3f();
                boolean leads = right == (-sx <= 0f);
                float back = leads ? 0.9f * more : 0f;
                return new Vector3f(-sx * back, 0f, -sz * back);
            }
        }
    }

    /**
     * The pose {@code t} seconds in, the soles {@code stand} of the way (0..1) to their stand.
     * {@code sz}: the forward part of the direction to the sound (-1 dead ahead, 1 behind).
     */
    public static Pose pose(int variant, int level, float t, float stand, float sz) {
        if (level <= 0 || t < 0f) return Pose.NONE;
        float more = MORE[level];
        stand = Math.max(0f, Math.min(1f, stand));
        // The jolt: on at once and gone in half a second.
        float over = jolt(level);
        float on = smooth(t / ATTACK), gone = 1f - smooth((t - over * 0.35f) / (over * 0.65f));
        double turn = Math.PI * 2 * (5.0 * t - 1.2 * t * t);
        float shudder = (variant == FREEZE ? 0.035f : 0.05f) * more * on * gone;
        // The shoulders: jerked up, and most of the way down again; what is left stays with the stand.
        float jerk = on * gone;
        float hopSeconds = hop(variant, level);
        float hop = hopSeconds > 0f && t < hopSeconds ? hopHeight(variant, level) * (float) Math.sin(Math.PI * t / hopSeconds) : 0f;
        // The look about: to a side and back, to the other and back, for as long as the stand is kept.
        float look = (float) Math.sin(Math.PI * 2 * Math.max(0f, t - 0.25f) / LOOK_SECONDS) * stand;
        float away = -sz;
        return switch (variant) {
            case RECOIL -> new Pose(shudder * (float) Math.sin(turn), 0.3f * shudder * (float) Math.cos(turn),
                    // Upright and leaning away from it: back from a sound ahead, forward from one behind.
                    -0.045f * more * away * stand, 0.9f * more * jerk + 0.25f * more * stand,
                    0f, 0.07f * more * stand, 0.8f * shudder * (float) Math.sin(turn - 1.1), 0f,
                    0f, 0f, Math.min(1f, 0.45f * more) * stand, hop, 0.75f);
            case JUMP -> new Pose(shudder * (float) Math.sin(turn), 0.3f * shudder * (float) Math.cos(turn),
                    // Down into the landing, and up out of it.
                    0.05f * more * landing(t, hopSeconds) + 0.02f * more * stand, 1.0f * more * jerk + 0.2f * more * stand,
                    0.11f * more * stand, 0.05f * more * stand, 0.8f * shudder * (float) Math.sin(turn - 1.1), 0.1f * more * look,
                    0.17f * more * look, 0.07f * more * landing(t, hopSeconds), level == STRONG ? 0.6f * stand : 0f, hop, 0.6f);
            default -> new Pose(shudder * (float) Math.sin(turn), 0.3f * shudder * (float) Math.cos(turn),
                    0.035f * more * stand, 1.1f * more * jerk + 0.45f * more * stand,
                    0.04f * more * stand, 0f, 0.6f * shudder * (float) Math.sin(turn - 1.1), 0f,
                    0f, 0.07f * more * stand, Math.min(1f, 0.55f * more) * slow(t, level) * stand, hop, 1f);
        };
    }

    /** The knees-less give of a landing: most just after the feet come down, eased off over half a second. */
    private static float landing(float t, float hopSeconds) {
        if (hopSeconds <= 0f) return 0f;
        return smooth((t - hopSeconds * 0.7f) / (hopSeconds * 0.5f)) * (1f - smooth((t - hopSeconds - 0.15f) / 0.5f));
    }

    /** One slow look: round to the sound and, before the stand is given up, back. */
    private static float slow(float t, int level) {
        return smooth((t - 0.15f) / 0.45f) * (1f - smooth((t - KEPT[level] * 0.7f) / (KEPT[level] * 0.3f)));
    }

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }
}
