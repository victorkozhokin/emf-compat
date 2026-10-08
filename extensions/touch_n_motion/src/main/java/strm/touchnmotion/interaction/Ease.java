package strm.touchnmotion.interaction;

/** The shaping curves every motion uses, over a share of the way from 0 to 1. */
public final class Ease {

    private Ease() {
    }

    /** {@code v} kept within 0..1. */
    public static float unit(float v) {
        return Math.max(0, Math.min(1, v));
    }

    public static double unit(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** 0 → 1 with no speed at either end; outside 0..1 it stays at the end it left. */
    public static float smooth(float v) {
        v = unit(v);
        return v * v * (3 - 2 * v);
    }

    public static double smooth(double v) {
        v = unit(v);
        return v * v * (3 - 2 * v);
    }
}
