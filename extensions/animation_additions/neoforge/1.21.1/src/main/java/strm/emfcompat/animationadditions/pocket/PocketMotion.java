package strm.emfcompat.animationadditions.pocket;

import strm.emfcompat.animationadditions.interaction.Ease;

/**
 * The stash gesture as curves over its phase, 0 to 1: in one sweep the hand comes up in front
 * swings out to the right and comes down round the hip into the back pocket, the torso bending over it and the head looking
 * down at it; then the hand comes back along the same arc and everything is where it was - the gesture ends in
 * the pose it left, so letting go of it shows nothing. Angles in radians, model space: +xRot
 * swings an arm back and tips the head down, +yRot turns to the right, +zRot lifts the right arm
 * out to the side.
 */
final class PocketMotion {
    static final double SECONDS = 2.45;

    /** The sweep ends over the pocket; by {@link #IN_POCKET} the hand is down in it, and from {@link #LEAVES} out and away. */
    static final float OVER_POCKET = .392f, IN_POCKET = .511f, LEAVES = .683f;
    private static final float BACK = .97f;
    /**
     * The arm and the head are asked for up to here, with the hand past the top of its way back:
     * the rest is the runtime giving them to the pack's pose, so a running player's arm is swinging
     * again at once instead of hanging still while the gesture runs out. The head and the torso
     * are back by then.
     */
    static final float LETS_GO = LEAVES + (BACK - LEAVES) * .6f;
    /** In the pocket the arm is back and turned in behind the hip, a little out so it clears the body's corner. */
    private static final float[] ARM_POCKET = {.38f, .5f, .1f}, HEAD_DOWN = {.5f, .5f};

    /**
     * {xRot, yRot, zRot, y} of the right arm: one arc, up and forward, then round the outside of the
     * body; it turns in only once it is behind, so it does not pass through the torso. It comes
     * back the way it went, but low - the same path walked, not swung - in 0.7 s. The last is the whole arm up or down, pixels, +y down:
     * the hand arrives raised, goes down into the pocket and is lifted out again.
     */
    static float[] arm(float phase) {
        boolean back = phase > LEAVES;
        float t = back ? 1 - smoother((phase - LEAVES) / (BACK - LEAVES)) : Ease.unit(phase / OVER_POCKET);
        float arc = (float) Math.sin(Math.PI * t) * (1 - .3f * t) * (back ? .35f : 1), in = Ease.smooth(t);
        // Out to the side late in the sweep, while the hand is already on its way down.
        float out = (float) Math.pow(Math.sin(Math.PI * Math.pow(t, 1.6)), 2) * (back ? .6f : 1);
        // While the arm is still in front, the same turn only carries the hand further out.
        float turned = Ease.smooth((t - .55f) / .45f);
        return new float[]{-1.2f * arc + ARM_POCKET[0] * in, ARM_POCKET[1] * turned, .42f * out + ARM_POCKET[2] * in,
                -2f * Ease.smooth((phase - .196f) / (OVER_POCKET - .196f)) + 2.7f * Ease.smooth((phase - OVER_POCKET) / (IN_POCKET - OVER_POCKET))
                        - 2.2f * Ease.smooth((phase - .59f) / (LEAVES - .59f)) + 1.5f * Ease.smooth((phase - LEAVES) / .25f)};
    }

    /** {xRot, yRot} the head adds to where it looks: down at the pocket and back. */
    static float[] head(float phase) {
        float look = Ease.smooth(phase / .34f) * (1 - Ease.smooth((phase - .6f) / (LETS_GO - .6f)));
        return new float[]{HEAD_DOWN[0] * look, HEAD_DOWN[1] * look};
    }

    /** How far the torso bends over the pocket, 0 to 1. */
    static float lean(float phase) {
        return Ease.smooth((phase - .089f) / .34f) * (1 - Ease.smooth((phase - .62f) / (LETS_GO - .62f)));
    }

    /** Whether the feet stand apart for the gesture; after it they step back one at a time. */
    static boolean apart(float phase) {
        return phase > .044f && phase < .62f;
    }

    private static float smoother(float v) {
        v = Ease.unit(v);
        return v * v * v * (v * (v * 6 - 15) + 10);
    }

    private PocketMotion() {
    }
}
