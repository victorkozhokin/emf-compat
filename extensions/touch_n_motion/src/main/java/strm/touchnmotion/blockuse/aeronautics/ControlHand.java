package strm.touchnmotion.blockuse.aeronautics;

/** A retained control keeps its hand until the control changes or the interaction ends. */
public final class ControlHand {
    private Object target;
    private int hand = -1;
    public int choose(Object control, double lateral) {
        if (control == null) { clear(); return -1; }
        if (!control.equals(target)) {
            target = control;
            hand = lateral >= 0 ? 0 : 1;
        }
        return hand;
    }
    public void clear() { target = null; hand = -1; }
}
