package strm.emfcompat.animationadditions.torso;

import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Smoothing;

/** Frame-safe smoothing for obstacle displacement and contacted upper-body pivots. */
public final class ClearanceOffset {
    private final Vector3f shown = new Vector3f();
    private float frame = Float.NaN;
    private long at;
    private final boolean snapFirst;

    public ClearanceOffset() { this(false); }
    public ClearanceOffset(boolean snapFirst) { this.snapFirst = snapFirst; }

    public Vector3f sample(float counter, long now, Vector3f target) {
        // EMF and biped/armour copies can ask for the pose in the same render frame.
        if (counter != frame) {
            double dt = at == 0 ? 0 : Math.max(0, Math.min(.1, (now - at) * 1e-9));
            if (at == 0 && snapFirst) shown.set(target);
            shown.lerp(target, Smoothing.follow(dt, .16));
            at = now;
            frame = counter;
        }
        return new Vector3f(shown);
    }
}
