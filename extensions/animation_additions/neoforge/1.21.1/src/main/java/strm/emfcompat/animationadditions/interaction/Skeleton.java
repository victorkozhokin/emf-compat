package strm.emfcompat.animationadditions.interaction;

import org.joml.Vector3f;

/**
 * The player model every feature measures against: vanilla pivots and lengths in model space -
 * pixels, y down, the model facing -z. The vectors are shared; never change one, copy it.
 */
public final class Skeleton {

    public static final Vector3f RIGHT_SHOULDER = new Vector3f(-5f, 2f, 0f);
    public static final Vector3f LEFT_SHOULDER = new Vector3f(5f, 2f, 0f);
    public static final Vector3f RIGHT_HIP = new Vector3f(-1.9f, 12f, 0f);
    public static final Vector3f LEFT_HIP = new Vector3f(1.9f, 12f, 0f);
    /** Where the torso turns: the bottom of the 12 px torso below the neck pivot. */
    public static final Vector3f WAIST = new Vector3f(0f, 12f, 0f);

    /**
     * The arm from the shoulder pivot, two lengths on purpose: to the fingertips for what a hand
     * takes hold of or presses (a handle, a button, a slot), to the palm for what it rests flat
     * against (a wall, the top of a plant).
     */
    public static final float ARM_TO_FINGERTIPS = 11f;
    public static final float ARM_TO_PALM = 10f;
    public static final float LEG = 12f;

    /** The player model's scale: model pixels are 1/16 of a block times this. */
    public static final float SCALE = 0.9375f;

    private Skeleton() {
    }
}
