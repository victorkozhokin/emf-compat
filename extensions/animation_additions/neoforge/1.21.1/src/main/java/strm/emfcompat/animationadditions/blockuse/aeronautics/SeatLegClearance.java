package strm.emfcompat.animationadditions.blockuse.aeronautics;

import org.joml.Vector3f;
import strm.emfcompat.animationadditions.blockuse.*;

/** Separating-axis test of the rendered thigh cuboid against a native seat collision box. */
public final class SeatLegClearance {
    static float penetration(Vector3f centre, Vector3f[] half, Vector3f min, Vector3f max) {
        var boxCentre = new Vector3f(min).add(max).mul(.5f);
        var extents = new Vector3f(max).sub(min).mul(.5f);
        var delta = new Vector3f(centre).sub(boxCentre);
        Vector3f[] basis = {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)};
        float depth = Float.POSITIVE_INFINITY;
        for (int i = 0; i < 15; i++) {
            Vector3f axis = i < 3 ? basis[i] : i < 6 ? half[i - 3] : new Vector3f(half[(i - 6) / 3]).cross(basis[(i - 6) % 3]);
            if (axis.lengthSquared() < 1e-10f) continue;
            float radius = 0;
            for (var h : half) radius += Math.abs(h.dot(axis));
            float box = extents.x * Math.abs(axis.x) + extents.y * Math.abs(axis.y) + extents.z * Math.abs(axis.z);
            float overlap = (radius + box - Math.abs(delta.dot(axis))) / axis.length();
            if (overlap <= 1e-4f) return 0;
            depth = Math.min(depth, overlap);
        }
        return depth;
    }
}
