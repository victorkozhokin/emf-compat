package strm.touchnmotion.footgrounding.compat;

import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;
import strm.touchnmotion.interaction.Ease;

/** Per-foot contact and swing, independent of Minecraft so a sampled gait can be replayed in tests. */
final class FootStep {
    static final double MAX_RISE = 10 * 0.9375 / 16;
    static final double CONTACT_EPS = 0.75 * 0.9375 / 16;
    boolean swinging, known;
    double plantedY, landingY, liftAt;
    float liftPitch, minPitch, progress, lastPitch = Float.NaN;
    float amp = 0.5f;
    double swingSeconds = 0.25;

    boolean descending() {
        return swinging && landingY < plantedY - CONTACT_EPS;
    }

    float anticipation() {
        if (!swinging || landingY <= plantedY) return 0;
        return (float) (Math.min(1, (landingY - plantedY) / MAX_RISE)
                * Math.pow(Math.sin(Math.PI * progress), 2));
    }

    void reset() {
        swinging = known = false;
        lastPitch = Float.NaN;
        progress = 0;
        plantedY = landingY = liftAt = 0;
        amp = 0.5f;
        swingSeconds = 0.25;
    }

    double update(float pitch, double now, double floor, DoubleSupplier predict) {
        if (!known) {
            plantedY = landingY = floor;
            known = true;
        }
        float turn = Float.isNaN(lastPitch) ? 0 : pitch - lastPitch;
        lastPitch = pitch;
        if (!swinging && turn < -0.001f) {
            swinging = true;
            liftAt = now;
            liftPitch = minPitch = pitch;
            progress = 0;
            landingY = validPrediction(predict.getAsDouble());
        } else if (swinging && turn > 0.001f) {
            swinging = false;
            swingSeconds = Math.max(0.1, Math.min(0.6, now - liftAt));
            amp = Math.max(0.15f, -minPitch);
            plantedY = landingY = floor;
        }
        double y = plantedY;
        if (swinging) {
            minPitch = Math.min(minPitch, pitch);
            float span = liftPitch + amp;
            progress = span < 0.001f ? 1 : Math.max(progress, Math.min(1, (liftPitch - pitch) / span));
            double prediction = validPrediction(predict.getAsDouble());
            // Keep the late landing stable, but never lock out a newly visible upward step.
            if (progress < 0.5f || prediction > landingY + CONTACT_EPS) landingY = prediction;
            y = swingHeight(plantedY, landingY, progress);
        }
        // Holding only world Y is insufficient when the animated sole moves across an edge.
        // A contact may hold above a drop; it must never hold inside a higher collision surface.
        if (reachable(plantedY, floor)) {
            y = Math.max(y, floor);
            if (!swinging) plantedY = y;
        }
        return y;
    }

    private double validPrediction(double y) {
        return reachable(plantedY, y) ? y : plantedY;
    }

    static boolean reachable(double from, double to) {
        return Double.isFinite(to) && Math.abs(to - from) <= MAX_RISE;
    }

    /** Six collision samples along a bounded path; stop at the first reachable upper level. */
    static double predictAlongPath(double planted, DoubleUnaryOperator floorAt) {
        double destination = planted;
        for (int i = 1; i <= 6; i++) {
            double y = floorAt.applyAsDouble(i / 6.0);
            if (!reachable(planted, y)) break;
            destination = y;
            if (y > planted + CONTACT_EPS) return y;
        }
        return destination;
    }

    static float landingPitch(float amplitude, float direction) {
        return -amplitude * direction;
    }

    /** Rise early, clear the lip, then settle. Flat ground receives no extra gait. */
    static double swingHeight(double from, double to, double progress) {
        double p = Ease.unit(progress);
        double rise = to - from;
        double t = rise > 0 ? Math.min(1, p / 0.65) : p;
        double y = from + rise * Ease.smooth(t);
        if (rise > 0) y += Math.min(rise * 0.2, 1.5 * 0.9375 / 16) * Math.pow(Math.sin(Math.PI * p), 2);
        return y;
    }

    /** Height gained by adding pitch/roll to an already rotated leg (ZYX, model +Y down). */
    static float rotationLift(float pitch, float yaw, float roll, float dp, float dr, float length) {
        return (float) (length * (soleY(pitch, yaw, roll) - soleY(pitch + dp, yaw, roll + dr)));
    }

    private static double soleY(float pitch, float yaw, float roll) {
        return Math.cos(roll) * Math.cos(pitch) + Math.sin(roll) * Math.sin(yaw) * Math.sin(pitch);
    }
}
