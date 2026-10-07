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
    public record Aim(Vector3f point, double quick, float weight, boolean twoHands, float freeSwing) {
    }

    /** Out over the water, a little further than the arm: the body is just drawn after it. */
    private static final Vector3f WAIT = new Vector3f(-5.5f, 8.5f, -9.6f);
    /** Straight up over the shoulder - not behind it, where a straight arm would have to turn right round - with the weight thrown back under it. */
    private static final Vector3f WOUND = new Vector3f(-6.5f, -9.5f, -0.5f);
    /** Flung out low before the body, well past arm's length. */
    private static final Vector3f THROWN = new Vector3f(-3.5f, 9.5f, -13.5f);
    /** Before the middle, both hands on it, the rod pulled down and away. */
    private static final Vector3f BITE = new Vector3f(-1.5f, 9f, -11.5f);
    /** Down and forward for an instant before the heave. */
    private static final Vector3f GATHERED = new Vector3f(-1.5f, 11.5f, -13f);
    /** Heaved up over the head, the weight thrown back. */
    private static final Vector3f HAULED = new Vector3f(-3f, -9f, -1f);
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
        return new Aim(new Vector3f(WAIT).add(0.35f * drift, 0.45f * breath + 0.25f * tip, 0.3f * breath), 0.12, -0.9f + 0.9f * drift, false, 0.05f * breath);
    }

    /** {@code seconds} into a bite: the rod snatched down and away, then worked against the pull. */
    public static Aim bite(float seconds) {
        float snatch = (float) Math.exp(-seconds * 6);
        float tug = 0.9f * (float) Math.sin(seconds * 27) * (float) Math.exp(-seconds * 2) + 0.6f * (float) Math.sin(seconds * 8.5);
        return new Aim(new Vector3f(BITE).add(0.3f * tug, 2.2f * snatch + 0.5f * tug, -1.5f * snatch - 0.6f * tug), 0.04, 1.6f + 1.4f * snatch + 0.4f * tug, true, 0f);
    }

    /** {@code seconds} into bringing the line in: a heave after a bite ({@code hooked}), a lift without. */
    public static Aim haul(float seconds, boolean hooked) {
        if (!hooked) return new Aim(new Vector3f(LIFTED), 0.07, -0.8f, false, -0.3f);
        if (seconds < GATHER) return new Aim(new Vector3f(GATHERED), 0.03, 2.6f, true, 0f);
        // Staggering back a little under what comes up, then steadied.
        float stagger = (float) Math.exp(-(seconds - GATHER) * 5);
        return new Aim(new Vector3f(HAULED), 0.045, -3.4f * stagger - 0.8f, seconds < HAUL * 0.7f, 0f);
    }

    /** {@code point}, given for a rod in the right hand, for the hand it is in. */
    public static Vector3f sided(Vector3f point, boolean right) {
        return right ? point : point.mul(-1f, 1f, 1f);
    }
}
