package strm.touchnmotion.ride;

/**
 * What a mount's body is like where a rider sits on it, in its own model's pixels (the renderer's
 * scale is {@code scale}): how wide the barrel under the saddle is, and - when its legs could be
 * told apart - where its four hooves stand and the ground they stand on.
 *
 * @param halfWidth half the barrel's width, model pixels
 * @param hooves    the hooves' centres {x, z}, model pixels, in the order right hind, left hind,
 *                  right front, left front (right is -x, hind is +z); {@code null} when not found
 * @param ground    the hooves' soles, model pixels down (the vanilla horse's is 24)
 * @param scale     how much larger than its model the renderer draws it
 * @param source    what measured it, for the log
 */
public record MountShape(float halfWidth, float[][] hooves, float ground, float scale, String source) {

    /** The vanilla horse, as the game draws it: the numbers the ride was tuned on. */
    public static final MountShape VANILLA_HORSE = new MountShape(5f,
            new float[][]{{-3f, 8f}, {3f, 8f}, {-3f, -9.9f}, {3f, -9.9f}}, 24f, 1.1f, "vanilla");

    /** Half the barrel's width in the rider's own model pixels. */
    public float riderHalfWidth() {
        return halfWidth * scale / strm.touchnmotion.interaction.Skeleton.SCALE;
    }

    /** From the hind hooves to the front ones, model pixels; 0 without hooves. */
    public float span() {
        return hooves == null ? 0f : (hooves[0][1] + hooves[1][1]) / 2f - (hooves[2][1] + hooves[3][1]) / 2f;
    }

    /** The middle of the hooves along the body, model pixels. */
    public float middleZ() {
        if (hooves == null) return 0f;
        return (hooves[0][1] + hooves[1][1] + hooves[2][1] + hooves[3][1]) / 4f;
    }
}
