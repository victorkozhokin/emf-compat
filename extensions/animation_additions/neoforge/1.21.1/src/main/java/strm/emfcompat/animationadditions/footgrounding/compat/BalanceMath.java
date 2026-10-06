package strm.emfcompat.animationadditions.footgrounding.compat;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

/** Straight-limb IK: retain the hip, redirect the sole, compensate the vertical displacement. */
final class BalanceMath {
    record Leg(float pitch, float yaw, float roll, float pivotY) {}
    record Sample(float x, float z, float height) {}

    /** Signed rightward imbalance. Start gently after 3 degrees, reach full at 12.
     * Smoothstep gives a quiet neutral pose and no abrupt threshold at either end. */
    static float counterbalance(float lean) {
        float strength = SupportSurface.clamp((Math.abs(lean) - (float) Math.toRadians(3))
                / (float) Math.toRadians(9), 0, 1);
        return Math.copySign(strength * strength * (3 - 2 * strength), lean);
    }

    /** Keep the rhythm of the pack, but soften extreme straight-leg sprint swings on beams. */
    static float balancePitch(float pitch, float weight) {
        float magnitude = Math.abs(pitch);
        if (magnitude <= 0.85f) return pitch;
        float limited = 0.85f + 0.4f * (float) Math.tanh((magnitude - 0.85f) / 0.4f);
        return Math.copySign(magnitude + (limited - magnitude) * weight, pitch);
    }

    static Leg leg(float pitch, float yaw, float roll, float length, float dx, float dz) {
        if (length < 1e-4f) return new Leg(pitch, yaw, roll, 0);
        Quaternionf rotation = new Quaternionf().rotationZYX(roll, yaw, pitch);
        Vector3f before = rotation.transform(new Vector3f(0, length, 0));
        float x = before.x + dx, z = before.z + dz;
        float horizontal = (float) Math.hypot(x, z);
        float max = length;
        if (horizontal > max) { x *= max / horizontal; z *= max / horizontal; }
        float y = Math.copySign((float) Math.sqrt(Math.max(0, length * length - x * x - z * z)), before.y);
        // Sprint poses can pass horizontal. Keep that hemisphere instead of folding the
        // limb through the torso, and never lift its hip several pixels to reach a beam.
        float boundedY = SupportSurface.clamp(y, before.y - 1.25f, before.y + 1.25f);
        if (boundedY != y) {
            float reach = (float) Math.sqrt(Math.max(0, length * length - boundedY * boundedY));
            float h = (float) Math.hypot(x, z);
            if (h > 1e-5f) { x *= reach / h; z *= reach / h; }
            else { x = reach; z = 0; }
            y = boundedY;
        }
        Vector3f after = new Vector3f(x, y, z);
        Quaternionf correction = new Quaternionf().rotationTo(before, after);
        Quaternionf q = correction.mul(rotation).normalize();
        // JOML 1.10.5's ZYX extraction has +y*y in the pitch denominator; use the
        // rotationZYX inverse explicitly so a yawed leg does not miss its contact.
        float px = (float) Math.atan2(2 * (q.w * q.x + q.y * q.z), 1 - 2 * (q.x * q.x + q.y * q.y));
        float py = (float) Math.asin(SupportSurface.clamp(2 * (q.w * q.y - q.z * q.x), -1, 1));
        float pz = (float) Math.atan2(2 * (q.w * q.z + q.x * q.y), 1 - 2 * (q.y * q.y + q.z * q.z));
        return new Leg(px, py, pz, before.y - y);
    }

    /** Model-space normal: upright is (0,-1,0). Return bounded pitch and roll. */
    static float[] slope(float nx, float ny, float nz) {
        float max = (float) Math.toRadians(8);
        return new float[]{SupportSurface.clamp((float) Math.atan2(-nz, -ny), -max, max),
                SupportSurface.clamp((float) Math.atan2(nx, -ny), -max, max)};
    }

    /** Voxel ramps have horizontal faces. Fit their sampled heights rather than treating
     * every small tread as a perfectly flat floor. Height is model y (positive down). */
    static float[] surfaceSlope(List<Sample> samples) {
        if (samples.size() < 3) return new float[2];
        double x = 0, z = 0, y = 0;
        for (Sample p : samples) { x += p.x; z += p.z; y += p.height; }
        x /= samples.size();
        z /= samples.size();
        y /= samples.size();
        double xx = 0, zz = 0, xz = 0, xy = 0, zy = 0;
        for (Sample p : samples) {
            double dx = p.x - x, dz = p.z - z, dy = p.height - y;
            xx += dx * dx;
            zz += dz * dz;
            xz += dx * dz;
            xy += dx * dy;
            zy += dz * dy;
        }
        double determinant = xx * zz - xz * xz;
        // A one-dimensional beam cannot establish the slope in its missing axis.
        if (determinant < 1e-5) return new float[2];
        return slope((float)((xy * zz - zy * xz) / determinant), -1,
                (float)((zy * xx - xy * xz) / determinant));
    }
}
