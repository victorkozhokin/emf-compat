package strm.emfcompat.animationadditions.wallhand;

import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.animationadditions.interaction.Ease;

/** Geometry and contact transitions independent of the Minecraft renderer. */
final class WallPoseMath {
    record Aim(float pitch, float roll) {}

    static float side(float previous, float right, float left, boolean engaged) {
        return engaged ? previous : right >= left ? 1 : -1;
    }

    static Aim aim(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (!Float.isFinite(length) || length < 1e-4f) return null;
        return new Aim((float) Math.asin(Math.max(-1, Math.min(1, z / length))),
                (float) Math.atan2(-x, y));
    }

    static float followAngle(float from, float to, float weight) {
        float delta = (float) Math.atan2(Math.sin(to - from), Math.cos(to - from));
        return from + delta * weight;
    }

    static final class Contact {
        float weight, pitch, roll;
        boolean known;

        void update(Aim target, float strength, double dt) {
            float wanted = target == null ? 0 : Ease.unit(strength);
            weight += (wanted - weight) * Smoothing.fadeIn(dt, wanted > weight ? 0.14 : 0.09);
            if (target != null) {
                float k = known ? Smoothing.follow(dt, 0.055) : 1;
                pitch = followAngle(pitch, target.pitch, k);
                roll = followAngle(roll, target.roll, k);
                known = true;
            } else if (weight < 0.001f) {
                known = false;
            }
        }
    }
}
