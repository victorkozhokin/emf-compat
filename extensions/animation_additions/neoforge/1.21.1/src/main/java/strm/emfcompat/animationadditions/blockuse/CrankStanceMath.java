package strm.emfcompat.animationadditions.blockuse;

import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Ease;

/** Pure stance geometry and angle continuity. */
final class CrankStanceMath {
    static float delta(float previous, float next) {
        float d = (next - previous) % 360;
        return d > 180 ? d - 360 : d < -180 ? d + 360 : d;
    }

    static float lift(float t) { return (float) Math.sin(Math.PI * Ease.unit(t)); }
    static Vector3f stance(boolean right, boolean crouch, int direction, boolean turning) {
        return turning ? new Vector3f(right ? -(crouch ? 1.5f : 1.2f) : (crouch ? 1.5f : 1.2f),
                0, (right ? 1 : -1) * direction * (crouch ? .5f : .8f)) : new Vector3f();
    }

}
