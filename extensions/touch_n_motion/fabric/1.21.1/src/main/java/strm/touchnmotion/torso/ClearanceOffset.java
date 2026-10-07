package strm.touchnmotion.torso;

import org.joml.Vector3f;
import strm.touchnmotion.interaction.Smoothing;
import strm.touchnmotion.interaction.FrameClock;

/** Frame-safe smoothing for obstacle displacement and contacted upper-body pivots. */
public final class ClearanceOffset {
    private final Vector3f shown = new Vector3f();
    private final FrameClock clock = new FrameClock();
    private final boolean snapFirst;

    public ClearanceOffset() { this(false); }
    public ClearanceOffset(boolean snapFirst) { this.snapFirst = snapFirst; }

    public Vector3f sample(float counter, long now, Vector3f target) {
        // EMF and biped/armour copies can ask for the pose in the same render frame.
        boolean first = clock.fresh();
        double dt = clock.tick(counter, now);
        if (dt >= 0) {
            if (first && snapFirst) shown.set(target);
            shown.lerp(target, Smoothing.follow(dt, .16));
        }
        return new Vector3f(shown);
    }
}
