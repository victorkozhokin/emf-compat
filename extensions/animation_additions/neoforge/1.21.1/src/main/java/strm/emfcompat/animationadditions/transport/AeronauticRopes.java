package strm.emfcompat.animationadditions.transport;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.animationadditions.interaction.SubLevels;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import strm.emfcompat.animationadditions.interaction.Body;

/** Optional Simulated physical strands: bounds first, then nearby segments, never block traversal. */
final class AeronauticRopes {
    private static boolean looked, available;
    private static Method manager, strands, strand, points, bounds, uuid, render;
    static final class Grip {
        final Level level;
        final UUID id;
        final int count;
        private double coordinate, wanted;
        private Vec3 last;
        Grip(Level level, UUID id, int segment, int count, double fraction, Vec3 point) {
            this.level = level;
            this.id = id;
            this.count = count;
            coordinate = wanted = segment + fraction;
            last = point;
        }
        private List<?> vertices() {
            Object s = call(strand, call(manager, null, level), id);
            return s == null ? null : (List<?>) call(points, s);
        }
        boolean valid() { var p = vertices(); return p != null && p.size() == count && count >= 2; }
        void retarget(Grip next) {
            if (level == next.level && id.equals(next.id) && count == next.count) wanted = next.coordinate;
        }
        void advance(double dt) { coordinate = RopePoseMath.slide(coordinate, wanted, dt); }
        Vec3 world() {
            var p = vertices();
            if (p == null || p.size() != count) return last;
            Vector3d a = RopePoseMath.sample(count, coordinate, i -> position(p.get(i)));
            last = new Vec3(a.x, a.y, a.z);
            return last;
        }
    }
    static SupportSearch.Contact find(AbstractClientPlayer player, IKFrame frame, List<SubLevels.Space> spaces, boolean right) {
        return find(player, frame, spaces, right, .25);
    }
    static SupportSearch.Contact find(AbstractClientPlayer player, IKFrame frame, List<SubLevels.Space> spaces, boolean right, double rise) {
        if (!ready()) return null;
        Vector3f shoulder = new Vector3f(right ? -5 : 5, 2, 0);
        Vec3 from = frame.jointWorld(shoulder);
        Vector3d wanted = new Vector3d(from.x, from.y + rise, from.z);
        SupportSearch.Contact best = null;
        double score = Double.POSITIVE_INFINITY;
        var area = player.getBoundingBox().inflate(1.1);
        for (Object s : (Iterable<?>) call(strands, call(manager, null, player.level()))) {
            if (!(call(bounds, s) instanceof net.minecraft.world.phys.AABB box) || !box.intersects(area)) continue;
            List<?> p = (List<?>) call(points, s);
            for (int i = 0; i + 1 < p.size(); i++) {
                Vector3d a = position(p.get(i)), b = position(p.get(i + 1));
                if (a.distanceSquared(b) < .0025) continue;
                double fraction = RopePoseMath.fraction(a, b, wanted);
                Vector3d point = new Vector3d(a).lerp(b, fraction);
                Vec3 world = new Vec3(point.x, point.y, point.z);
                Vector3f model = Body.model(frame, world);
                if (world.y < player.getY() + .7 || !RopePoseMath.reachable(model, shoulder, right)) continue;
                double distance = point.distance(wanted);
                if (distance < score && SupportSearch.clear(player, from, world, spaces)) {
                    score = distance;
                    var grip = new Grip(player.level(), (UUID) call(uuid, s), i, p.size(), fraction, world);
                    best = new SupportSearch.Contact(SubLevels.WORLD, null, world, Vec3.ZERO, false, grip);
                }
            }
        }
        return best;
    }
    private static Vector3d position(Object point) {
        return (Vector3d) call(render, point, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false), new Vector3d());
    }
    private static boolean ready() {
        if (!looked) {
            looked = true;
            try {
                String base = "dev.simulated_team.simulated.content.blocks.rope.strand.client.";
                Class<?> m = Class.forName(base+"ClientLevelRopeManager"), s = Class.forName(base+"ClientRopeStrand");
                manager = m.getMethod("getOrCreate", Level.class);
                strands = m.getMethod("getAllStrands");
                strand = m.getMethod("getStrand", UUID.class);
                points = s.getMethod("getPoints");
                bounds = s.getMethod("getBounds");
                uuid = s.getMethod("getUuid");
                render = Class.forName(base+"ClientRopePoint").getMethod("renderPos", float.class, Vector3d.class);
                available = true;
            } catch (ClassNotFoundException absent) { available = false; }
            catch (ReflectiveOperationException e) { org.slf4j.LoggerFactory.getLogger("EMFCompatTransport").warn("Aeronautics rope API unavailable", e); }
        }
        return available;
    }
    private static Object call(Method m, Object receiver, Object... args) {
        try { return m.invoke(receiver, args); } catch (ReflectiveOperationException e) { throw new IllegalStateException("Aeronautics rope "+m.getName(), e); }
    }
}
