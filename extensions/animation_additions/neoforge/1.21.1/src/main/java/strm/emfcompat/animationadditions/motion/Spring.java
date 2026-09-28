package strm.emfcompat.animationadditions.motion;

/**
 * A critically damped spring: a value that goes after its target with a velocity of its own, so
 * it eases in and out and a change of target mid-way carries its momentum over instead of
 * snapping. Exact for any time step (after Daniel Holden's "Spring-It-On"), so it moves the same at
 * 30 or 144 fps.
 *
 * <p>{@code halflife}, seconds: how long it takes to cover half the way to a target standing still.</p>
 */
public final class Spring {

    private static final double LN2 = Math.log(2);

    public float value;
    public float velocity;

    /** Goes {@code dt} seconds towards {@code target}. */
    public void update(float target, double halflife, double dt) {
        if (dt <= 0) return;
        if (halflife <= 1e-4) {
            velocity = 0f;
            value = target;
            return;
        }
        double y = 2 * LN2 / halflife;
        double j0 = value - target;
        double j1 = velocity + j0 * y;
        double e = Math.exp(-y * dt);
        value = (float) (e * (j0 + j1 * dt) + target);
        velocity = (float) (e * (velocity - j1 * y * dt));
    }

    /** Settles on {@code v} at once, still. */
    public void set(float v) {
        value = v;
        velocity = 0f;
    }
}
