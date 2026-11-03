package strm.touchnmotion.ride;

import strm.emfcompat.core.ik.Skeleton;
/**
 * How a rider's legs lie along a mount's sides, from how wide it is - plain geometry, no game
 * classes, so it can be tested.
 *
 * <p>The legs are straight, twelve pixels, four wide, and hang from hips that may not go further out
 * than the rider's own torso (four pixels): further, the legs part from the pelvis. So the hips go out
 * towards the barrel's edge as far as that allows, and the leg is turned out only as much as it takes
 * for its foot to come just past the side - along the barrel, not splayed off it.</p>
 */
public final class MountMath {

    /** Rider pixels: the leg's length; the hips' own place; as far out as they go; where the foot ends, past the side. */
    static final float LEG = Skeleton.LEG, HIP = 1.9f, HIP_MOST = 4.5f, FOOT_OUT = 1f;
    /** Radians: the least and the most the leg is turned out; the foot a little forward, as in a stirrup. */
    static final float ROLL_LEAST = 0.06f, ROLL_MOST = 0.6f, FORWARD = -0.15f;
    /** The vanilla horse's barrel, half its width in rider pixels: the reins were placed on it. */
    public static final float VANILLA_HALF_WIDTH = 5f * 1.1f / 0.9375f;

    private MountMath() {
    }

    /** The legs on a barrel {@code halfWidth} rider pixels across from the middle: {hip x, turn out, forward}. */
    public static float[] legs(float halfWidth) {
        float hip = Math.max(HIP, Math.min(HIP_MOST, halfWidth - 1.5f));
        float sin = (halfWidth + FOOT_OUT - hip) / LEG;
        float roll = (float) Math.asin(Math.max(Math.sin(ROLL_LEAST), Math.min(Math.sin(ROLL_MOST), sin)));
        return new float[]{hip, roll, FORWARD};
    }

    /** How much further the reins are on a mount this wide than on the vanilla horse, kept to bodies the ride can reach. */
    public static float reinsScale(float halfWidth) {
        return Math.max(0.8f, Math.min(1.25f, halfWidth / VANILLA_HALF_WIDTH));
    }

    /** How far into the barrel the middle of a leg goes at its deepest, rider pixels (0 when it is outside). */
    public static float embedding(float halfWidth, float hip) {
        return Math.max(0f, halfWidth - hip);
    }
}
