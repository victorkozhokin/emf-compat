package strm.emfcompat.animationadditions.fishing;

import org.joml.Vector3f;

/**
 * The shape of fishing, as numbers. No game in here - only time in, and out of it where the rod
 * hand is wanted and how hard the work is.
 *
 * <p>What is animated is one point: the place of the fist on the rod, model pixels, for a rod in
 * the right hand (the right is -x, forward -z, up -y; the right shoulder is at (-5, 2, 0)). Some
 * of its places are further than an arm is long, on purpose: the hand is sent there, and the body
 * has to come after it - lean, turn, shift its weight - to let it arrive. So the arm is quick and
 * the body is late and soft behind it, which is where the life of the thing is. The second hand,
 * the feet and the free arm hang off the same point and the same effort.</p>
 */
public final class FishingMotion {

    /** Seconds: the cast from its first move to the wait; the haul after a bite, and without one. */
    public static final float CAST = 0.55f, HAUL = 0.7f, LIFT = 0.5f;
    private static final float WIND = 0.16f, THROW = 0.3f, GATHER = 0.09f;

    /**
     * Where the fist is wanted, how soon it gets there (the half-life of its going, seconds), the
     * weight sent forward (model pixels, forward positive), whether the second hand is on the rod,
     * and the free arm's swing: forward positive, radians.
     */
    public record Aim(Vector3f point, double quick, float weight, boolean twoHands, float freeSwing, float turn, float forward, float aside, int stance) {
        Aim(Vector3f point, double quick, float weight, boolean twoHands, float freeSwing) {
            this(point, quick, weight, twoHands, freeSwing, 0f, 0f, 0f, 0);
        }
    }

    /** Radians: bringing the line in the body turns this far to the rod's side, the rod swept round with it; waiting it leans this far forward and away from the rod. */
    private static final float SWEEP = (float) Math.toRadians(30), WAIT_FORWARD = 0.07f, WAIT_ASIDE = 0.07f;

    /**
     * Where the soles stand, model pixels from where the pack has them, for a rod in the right
     * hand: {right foot, left foot} for the wait, the bite and the haul - each a little wider and
     * longer than the last, so the feet are shifted a step at each.
     */
    private static final Vector3f[][] STANCES = {
            {new Vector3f(-0.6f, 0f, 2.4f), new Vector3f(0.3f, 0f, -1.6f)},
            {new Vector3f(-1.2f, 0f, 3.0f), new Vector3f(0.8f, 0f, -2.3f)},
            {new Vector3f(-2.4f, 0f, 3.4f), new Vector3f(2.0f, 0f, -1.9f)}};

    /** The sole's place in stance {@code stance} for the right foot or the left, the rod in the right hand or the left. */
    public static Vector3f foot(int stance, boolean rightFoot, boolean rodRight) {
        // Mirrored, the right foot stands where the left one did, the other way out.
        Vector3f at = STANCES[stance][rightFoot == rodRight ? 0 : 1];
        return rodRight ? new Vector3f(at) : new Vector3f(-at.x, at.y, at.z);
    }

    /** Out over the water, further than the arm: the body leans a little forward after it. */
    private static final Vector3f WAIT = new Vector3f(-5.5f, 8.5f, -11.4f);
    /** Straight up over the shoulder - not behind it, where a straight arm would have to turn right round - with the weight thrown back under it. */
    private static final Vector3f WOUND = new Vector3f(-6.5f, -9.5f, -0.5f);
    /** Flung out low before the body, well past arm's length. */
    private static final Vector3f THROWN = new Vector3f(-3.5f, 9.5f, -13.5f);
    /** Before the middle, both hands on it, the rod pulled down and away. */
    private static final Vector3f BITE = new Vector3f(-1.5f, 9f, -11.5f);
    /** Down and forward for an instant before the heave. */
    private static final Vector3f GATHERED = new Vector3f(-1.5f, 10.5f, -12f);
    /** Heaved up to the chest's height before the body, off to the rod's side - clear of the head and the face - the weight thrown back. */
    private static final Vector3f HAULED = new Vector3f(-5.5f, 1.5f, -8.5f);
    /** Raised in one hand, nothing on it. */
    private static final Vector3f LIFTED = new Vector3f(-5.5f, -3.5f, -5f);

    private FishingMotion() {
    }

    /** {@code seconds} into the cast. */
    public static Aim cast(float seconds) {
        if (seconds < WIND) return new Aim(new Vector3f(WOUND), 0.045, -3.2f, false, 0.9f);
        if (seconds < THROW) return new Aim(new Vector3f(THROWN), 0.022, 3.2f, false, -1.0f);
        return new Aim(new Vector3f(WAIT), 0.07, 0.4f, false, 0f);
    }

    /** Waiting, {@code seconds} into it: breath, the tip riding, the weight drifting from foot to foot. */
    public static Aim waiting(float seconds) {
        float breath = (float) Math.sin(seconds * 1.7), drift = (float) Math.sin(seconds * 0.45 + 1), tip = (float) Math.sin(seconds * 2.9 + 0.5);
        return new Aim(new Vector3f(WAIT).add(0.35f * drift, 0.45f * breath + 0.25f * tip, 0.3f * breath), 0.12, 0.7f + 0.8f * drift, false, 0.05f * breath,
                0f, WAIT_FORWARD, WAIT_ASIDE, 0);
    }

    /** {@code seconds} into a bite: the rod snatched down and away, then worked against the pull. */
    public static Aim bite(float seconds) {
        float snatch = (float) Math.exp(-seconds * 6);
        float tug = 0.9f * (float) Math.sin(seconds * 27) * (float) Math.exp(-seconds * 2) + 0.6f * (float) Math.sin(seconds * 8.5);
        return new Aim(new Vector3f(BITE).add(0.3f * tug, 2.2f * snatch + 0.5f * tug, -1.5f * snatch - 0.6f * tug), 0.04, 1.6f + 1.4f * snatch + 0.4f * tug, true, 0f, 0f, 0f, 0f, 1);
    }

    /** {@code seconds} into bringing the line in: a heave after a bite ({@code hooked}), a lift without. */
    public static Aim haul(float seconds, boolean hooked) {
        if (!hooked) return new Aim(new Vector3f(LIFTED), 0.07, -0.8f, false, -0.3f, SWEEP, 0f, 0f, 2);
        if (seconds < GATHER) return new Aim(new Vector3f(GATHERED), 0.035, 2.0f, true, 0f, 0f, 0f, 0f, 2);
        // Staggering back a little under what comes up, then steadied.
        float stagger = (float) Math.exp(-(seconds - GATHER) * 5);
        return new Aim(new Vector3f(HAULED), 0.06, -2.2f * stagger - 0.6f, seconds < HAUL * 0.7f, 0f, SWEEP, 0f, 0f, 2);
    }

    /** Seconds: the easing down after the line is in - after a catch, and after a lift with nothing on it. */
    public static final float EASE = 0.95f, EASE_EMPTY = 0.65f;
    /** All but where the hand hangs with a rod in it: from here, giving the pose up shows nothing. */
    private static final Vector3f RESTED = new Vector3f(-6f, 10.3f, -2.5f);

    /**
     * {@code seconds} into easing down once the line is in. One unhurried move and no stops in it:
     * the rod sinks from where the haul left it to where the hand hangs, and the turn, the weight
     * and the wide stance are given back along the same curve.
     */
    public static Aim ease(float seconds, boolean hooked) {
        float done = seconds / ((hooked ? EASE : EASE_EMPTY) * 0.85f);
        done = done >= 1f ? 1f : done <= 0f ? 0f : done * done * (3f - 2f * done);
        Vector3f point = new Vector3f(hooked ? HAULED : LIFTED).lerp(RESTED, done);
        return new Aim(point, 0.16, (hooked ? -0.6f : -0.3f) * (1f - done), false, 0f, SWEEP * (1f - done), 0f, 0f, done < 0.45f ? 2 : done < 0.8f ? 1 : 0);
    }

    /** {@code point}, given for a rod in the right hand, for the hand it is in. */
    public static Vector3f sided(Vector3f point, boolean right) {
        return right ? point : point.mul(-1f, 1f, 1f);
    }
}
