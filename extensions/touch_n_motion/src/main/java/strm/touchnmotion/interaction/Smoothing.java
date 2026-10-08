package strm.touchnmotion.interaction;

/**
 * Exponential approach factors: move {@code k} of the way to the target each frame, with {@code k}
 * worked out from the time since the last frame, so the curve is the same at 30 or 144 fps.
 */
public final class Smoothing {

    private Smoothing() {
    }

    /** The share of the way to go in {@code dt} seconds with time constant {@code seconds}. */
    public static float step(double dt, double seconds) {
        if (seconds <= 0) return 1f;
        return (float) (1 - Math.exp(-dt / seconds));
    }

    /** Following a moving aim: 0 seconds follows at once. */
    public static float follow(double dt, double seconds) {
        return step(dt, seconds);
    }

    /** A fade in: nothing on the very first frame (dt 0), so it does start from zero. */
    public static float fadeIn(double dt, double seconds) {
        return dt == 0 ? 0f : step(dt, seconds);
    }

    /** A fade out: done at once on the very first frame (dt 0) - nothing to fade from. */
    public static float fadeOut(double dt, double seconds) {
        return snapFirst(dt, seconds);
    }

    /** Settling on a value: straight to it on the very first frame (dt 0), then smoothly. */
    public static float snapFirst(double dt, double seconds) {
        return dt == 0 ? 1f : step(dt, seconds);
    }
}
