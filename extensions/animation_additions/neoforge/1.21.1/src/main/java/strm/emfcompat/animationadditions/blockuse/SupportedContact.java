package strm.emfcompat.animationadditions.blockuse;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Temporally coherent fitting for sustained, bilateral surface contacts. */
final class SupportedContact {
    static Quaternionf fit(Quaternionf previous, Vector3f right, Vector3f left, Vector3f rt, Vector3f lt) {
        Vector3f origin = previous.getEulerAnglesZYX(new Vector3f()), angles = new Vector3f(origin);
        float best = score(angles, origin, right, left, rt, lt);
        // Warm start: do not switch to an unrelated equivalent yaw/roll solution each draw.
        for (float degrees : new float[]{4, 2, 1, .5f, .25f, .125f}) for (int pass = 0; pass < 3; pass++) for (int axis = 0; axis < 3; axis++) {
            Vector3f chosen = new Vector3f(angles);
            for (int sign : new int[]{-1, 1}) {
                var candidate = new Vector3f(angles);
                candidate.setComponent(axis, candidate.get(axis) + sign * (float) Math.toRadians(degrees));
                float limit = (float) Math.toRadians(30);
                if (candidate.length() > limit) candidate.normalize(limit);
                float next = score(candidate, origin, right, left, rt, lt);
                if (next < best) { best = next; chosen.set(candidate); }
            }
            angles.set(chosen);
        }
        return new Quaternionf().rotationZYX(angles.z, angles.y, angles.x);
    }
    static Quaternionf follow(Quaternionf previous, Quaternionf wanted, double dt) {
        if (dt <= 0) return new Quaternionf(previous);
        float distance = 2 * (float) Math.acos(Math.min(1, Math.abs(previous.dot(wanted))));
        float blend = (float)(1 - Math.exp(-Math.max(0, dt) / .12));
        if (distance > 1e-6f) blend = Math.min(blend, (float) Math.toRadians(60) * Math.max(0, (float) dt) / distance);
        return new Quaternionf(previous).slerp(wanted, blend);
    }
    private static float score(Vector3f a, Vector3f origin, Vector3f right, Vector3f left, Vector3f rt, Vector3f lt) {
        var q = new Quaternionf().rotationZYX(a.z, a.y, a.x);
        float r = q.transform(new Vector3f(right)).distance(rt) - 11, l = q.transform(new Vector3f(left)).distance(lt) - 11;
        return r * r + l * l + .15f * a.lengthSquared() + 2 * new Vector3f(a).sub(origin).lengthSquared();
    }
}
