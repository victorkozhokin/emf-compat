package strm.touchnmotion.interaction;

import traben.entity_model_features.models.animation.state.EMFState;

/**
 * Time for state that must move once a drawn frame: EMF and the biped and armour copies ask for
 * the same pose several times in one frame, and only the first of them may advance anything.
 */
public final class FrameClock {

    /** The longest step taken at once, seconds: after a pause the motion goes on, it does not jump. */
    public static final double LONGEST = .1;

    private float frame = Float.NaN;
    private long at;

    /**
     * Seconds since the frame before - 0 on the very first one - or a negative number when this
     * frame has been counted already.
     */
    public double tick() {
        return tick(EMFState.getFrameCounter(), System.nanoTime());
    }

    public double tick(float counter, long now) {
        if (counter == frame) return -1;
        double dt = at == 0 ? 0 : Math.max(0, Math.min(LONGEST, (now - at) * 1e-9));
        at = now;
        frame = counter;
        return dt;
    }

    /** Forgets everything: the next frame is the first again. */
    public void reset() {
        frame = Float.NaN;
        at = 0;
    }

    /** Forgets the time only: the next new frame takes no step. */
    public void restart() {
        at = 0;
    }

    /** Whether nothing has been counted yet. */
    public boolean fresh() {
        return at == 0;
    }
}
