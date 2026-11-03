package strm.touchnmotion.wallhand;

import strm.emfcompat.core.ik.IKMath;
import strm.touchnmotion.interaction.Smoothing;
import strm.touchnmotion.interaction.Ease;

/** Geometry and contact transitions independent of the Minecraft renderer. */
final class WallPoseMath {
    record Aim(float pitch, float roll) {}

    static float side(float previous, float right, float left, boolean engaged) {
        return engaged ? previous : right >= left ? 1 : -1;
    }

    static Aim aim(float x, float y, float z) {
        IKMath.Angles a = IKMath.anglesXZDownward(x, y, z);
        return a == null ? null : new Aim(a.x(), a.secondary());
    }

    static final class Contact {
        float weight, pitch, roll;
        boolean known;

        void update(Aim target, float strength, double dt) {
            float wanted = target == null ? 0 : Ease.unit(strength);
            weight += (wanted - weight) * Smoothing.fadeIn(dt, wanted > weight ? 0.14 : 0.09);
            if (target != null) {
                float k = known ? Smoothing.follow(dt, 0.055) : 1;
                pitch = IKMath.approachAngle(pitch, target.pitch, k);
                roll = IKMath.approachAngle(roll, target.roll, k);
                known = true;
            } else if (weight < 0.001f) {
                known = false;
            }
        }
    }
}
