package strm.touchnmotion.buttonpress;

import strm.emfcompat.core.ik.IKMath;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.touchnmotion.interaction.Ease;

/** Pure reach envelopes: continuous at the edge of reach and independent of frame rate. */
public final class ReachEnvelope {
    private ReachEnvelope() {}

    /** An overhead target asks for gradual extension; low targets must not stand the player up. */
    public static float upright(float x, float y, float z, float length) {
        if (y >= 0) return 0;
        float distance = (float) Math.sqrt(x * x + y * y + z * z);
        return Ease.smooth((distance - length) / 4f) * Ease.smooth(-y / 3f);
    }

    public static float follow(float current, float target, double dt) {
        if (dt <= 0) return current;
        double tau = target > current ? 0.16 : 0.24;
        return current + (target - current) * (float) - Math.expm1(-dt / tau);
    }

    /** Smooth the contact correction itself, including its return to identity. */
    public static Quaternionf followContact(Quaternionf current, Quaternionf target, double dt) {
        float alpha = (float) - Math.expm1(-Math.max(0, Math.min(dt, 0.1)) / 0.18);
        return current.slerp(target, alpha).normalize();
    }

    /** Minimal rotation about the waist that brings a rigid arm within reach; never stretches bones. */
    public static Quaternionf contactTurn(Vector3f shoulder, Vector3f target, float arm, float limit) {
        if (shoulder.distance(target) <= arm) return new Quaternionf();
        return IKMath.reachTurn(shoulder, target, arm, 0f, limit);
    }
}
