package strm.emfcompat.animationadditions.footgrounding.compat;

import java.util.List;

/** A sampled collision surface in model pixels. No block names or render shapes involved. */
final class SupportSurface {
    record Point(float x, float z) {}
    record Placement(float x, float z) {}
    final List<Point> points;
    final float narrow;
    private final float cx, cz, nx, nz;

    SupportSurface(List<Point> points, float spacing) {
        this.points = List.copyOf(points);
        float x = 0, z = 0;
        for (Point p : points) { x += p.x; z += p.z; }
        cx = points.isEmpty() ? 0 : x / points.size();
        cz = points.isEmpty() ? 0 : z / points.size();
        float xx = 0, zz = 0, xz = 0;
        for (Point p : points) {
            float dx = p.x - cx, dz = p.z - cz;
            xx += dx * dx; zz += dz * dz; xz += dx * dz;
        }
        // Principal long axis; the normal is where the stance needs to become narrower.
        double angle = 0.5 * Math.atan2(2 * xz, xx - zz);
        nx = (float) -Math.sin(angle); nz = (float) Math.cos(angle);
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (Point p : points) {
            float across = (p.x - cx) * nx + (p.z - cz) * nz;
            min = Math.min(min, across); max = Math.max(max, across);
        }
        float width = max - min + spacing;
        narrow = points.isEmpty() ? 0 : clamp((7 - width) / 4, 0, 1);
    }

    /** Pull the sole towards the support's centre line, then verify it against actual samples.
     * The final nearest-sample check also handles corners, islands and disconnected surfaces. */
    Placement place(float x, float z) {
        if (narrow == 0) return new Placement(x, z);
        float across = (x - cx) * nx + (z - cz) * nz;
        float tx = x - across * nx, tz = z - across * nz;
        Point best = null;
        float distance = Float.POSITIVE_INFINITY;
        for (Point p : points) {
            float d = (p.x - tx) * (p.x - tx) + (p.z - tz) * (p.z - tz);
            if (d < distance) { distance = d; best = p; }
        }
        if (best == null) return new Placement(x, z);
        // On the supported strip preserve the along-path stride without grid-sized snapping.
        if (distance > 0.8f * 0.8f) { tx = best.x; tz = best.z; }
        float dx = (tx - x) * narrow, dz = (tz - z) * narrow;
        float length = (float) Math.hypot(dx, dz);
        float limit = length > 6 ? 6 / length : 1;
        return new Placement(x + dx * limit, z + dz * limit);
    }

    static float clamp(float v, float low, float high) { return Math.max(low, Math.min(high, v)); }
}
