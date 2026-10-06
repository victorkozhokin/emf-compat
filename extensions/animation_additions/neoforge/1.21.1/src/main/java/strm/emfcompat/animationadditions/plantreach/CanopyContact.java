package strm.emfcompat.animationadditions.plantreach;

import java.util.ArrayList;
import java.util.List;

/** Intersections of a straight arm's horizontal reach circle with a plant's top rectangle. */
public final class CanopyContact {
    record Point(double x, double z) {}
    public record Edge(double x, double y, double z) {}

    /** Nearest rim follows the shoulder along the row; height fits the unextended arm. */
    public static Edge edge(double sx, double sy, double sz, double arm,
                     double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
        double x = Math.max(minX, Math.min(maxX, sx)), z = Math.max(minZ, Math.min(maxZ, sz));
        // A shoulder within the footprint has no outward rim to brush safely.
        if (sx > minX && sx < maxX && sz > minZ && sz < maxZ) return null;
        double vertical2 = arm * arm - (x - sx) * (x - sx) - (z - sz) * (z - sz);
        if (vertical2 < 0) return null;
        double y = sy - Math.sqrt(vertical2);
        return y >= minY && y <= maxY ? new Edge(x, y, z) : null;
    }
    static List<Point> points(double sx, double sz, double dy, double arm,
                              double minX, double maxX, double minZ, double maxZ, double hx, double hz) {
        List<Point> out = new ArrayList<>(9);
        double radius2 = arm * arm - dy * dy;
        if (radius2 < 0) return out;
        double radius = Math.sqrt(radius2), angle = Math.atan2(hz - sz, hx - sx);
        add(out, sx + radius * Math.cos(angle), sz + radius * Math.sin(angle), minX, maxX, minZ, maxZ);
        for (double x : new double[]{minX, maxX}) {
            double zz = radius2 - (x - sx) * (x - sx);
            if (zz >= 0) for (double sign : new double[]{-1, 1})
                add(out, x, sz + sign * Math.sqrt(zz), minX, maxX, minZ, maxZ);
        }
        for (double z : new double[]{minZ, maxZ}) {
            double xx = radius2 - (z - sz) * (z - sz);
            if (xx >= 0) for (double sign : new double[]{-1, 1})
                add(out, sx + sign * Math.sqrt(xx), z, minX, maxX, minZ, maxZ);
        }
        return out;
    }
    private static void add(List<Point> out, double x, double z, double minX, double maxX, double minZ, double maxZ) {
        if (x >= minX - 1e-8 && x <= maxX + 1e-8 && z >= minZ - 1e-8 && z <= maxZ + 1e-8) out.add(new Point(x, z));
    }
    private CanopyContact() {}
}
