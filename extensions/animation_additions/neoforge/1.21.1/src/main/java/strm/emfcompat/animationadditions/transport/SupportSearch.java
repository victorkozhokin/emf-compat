package strm.emfcompat.animationadditions.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.SubLevels;
import strm.emfcompat.core.ik.IKFrame;
import java.util.List;
import strm.emfcompat.animationadditions.interaction.Body;
import strm.emfcompat.animationadditions.transport.aeronautics.AeronauticRopes;

/** Short local rays. Collision surfaces, or continuous slender outline-only supports. */
public final class SupportSearch {
    public record Contact(SubLevels.Space space, BlockPos block, Vec3 point, Vec3 normal, boolean outline, AeronauticRopes.Grip rope) {
        public Contact(SubLevels.Space space, BlockPos block, Vec3 point, Vec3 normal, boolean outline) { this(space, block, point, normal, outline, null); }
        Vec3 world() { return rope == null ? space.refresh().toWorld(point) : rope.world(); }
    }
    record Deck(SubLevels.Space space, Vec3 local, Vec3 normal) {}
    static Deck deck(AbstractClientPlayer player, List<SubLevels.Space> spaces) {
        Vec3 from = player.position().add(0, .16, 0), to = player.position().add(0, -.36, 0);
        Deck best = null;
        double nearest = 1;
        for (var space : spaces) {
            var hit = player.level().clip(new ClipContext(space.toLocal(from), space.toLocal(to),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            if (space.isWorld() && !SubLevels.at(player.level(), hit.getBlockPos()).isWorld()) continue;
            Vec3 normal = space.directionToWorld(Vec3.atLowerCornerOf(hit.getDirection().getNormal())).normalize();
            double distance = space.toWorld(hit.getLocation()).distanceTo(from);
            if (normal.y > .5 && distance < nearest) { nearest = distance; best = new Deck(space, hit.getLocation(), normal); }
        }
        return best == null || best.space.isWorld() ? null : best;
    }
    public static Contact find(AbstractClientPlayer player, IKFrame frame, List<SubLevels.Space> spaces, boolean right) {
        var physical = AeronauticRopes.find(player, frame, spaces, right);
        if (physical != null) return physical;
        Vector3f shoulder = new Vector3f(right ? -5 : 5, 2, 0);
        Vec3 start = frame.jointWorld(shoulder);
        Contact best = null;
        double score = Double.POSITIVE_INFINITY;
        int hits = 0, eligible = 0, reachable = 0;
        // 66 rays per hand, each shorter than a block; no block-radius traversal.
        for (float angle : new float[]{-70, -50, -30, -15, 0, 15, 30, 45, 60, 80, 110}) for (float rise : new float[]{-.55f, -.25f, .05f, .3f, .6f, .9f}) {
            double a = Math.toRadians(angle);
            float sign = right ? -1 : 1;
            Vector3f direction = new Vector3f(sign * (float) Math.sin(a), rise, -(float) Math.cos(a)).normalize().mul(14);
            Vec3 end = frame.jointWorld(new Vector3f(shoulder).add(direction));
            for (var space : spaces) for (boolean outline : new boolean[]{false, true}) {
                var hit = player.level().clip(new ClipContext(space.toLocal(start), space.toLocal(end),
                        outline ? ClipContext.Block.OUTLINE : ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (hit.getType() != HitResult.Type.BLOCK || hit.isInside()) continue;
                if (space.isWorld() && !SubLevels.at(player.level(), hit.getBlockPos()).isWorld()) continue;
                hits++;
                var state = player.level().getBlockState(hit.getBlockPos());
                if (state.canBeReplaced() || !state.getFluidState().isEmpty()) continue;
                if (outline && !state.getCollisionShape(player.level(), hit.getBlockPos(), net.minecraft.world.phys.shapes.CollisionContext.of(player)).isEmpty()) continue;
                if (outline && !continuous(player, hit.getBlockPos())) continue;
                Vec3 point = space.toWorld(hit.getLocation());
                if (point.y < player.getY() + .6) continue;
                eligible++;
                Vector3f model = Body.model(frame, point);
                if (!BraceMath.reachable(model, shoulder, right, false)) continue;
                reachable++;
                double d = Math.abs(model.distance(shoulder) - 11) + (model.z > 2 ? 2 : 0) + (outline ? -.25 : 0);
                if (d < score && clear(player, start, point, spaces)) {score = d; best = new Contact(space, hit.getBlockPos().immutable(), hit.getLocation(),
                        Vec3.atLowerCornerOf(hit.getDirection().getNormal()), outline);}
            }
        }
        if (strm.emfcompat.core.EMFCompatConfig.getBoolean(TransportGrip.KEY_TRACE, false))
            org.slf4j.LoggerFactory.getLogger("EMFCompatTransport").info("[SupportSearch] right={} hits={} eligible={} reachable={} found={}", right, hits, eligible, reachable, best != null);
        return best;
    }
    public static boolean clear(AbstractClientPlayer player, Vec3 from, Vec3 to, List<SubLevels.Space> spaces) {
        double reach = from.distanceTo(to);
        for (var space : spaces) {
            var hit = player.level().clip(new ClipContext(space.toLocal(from), space.toLocal(to),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            if (space.isWorld() && !SubLevels.at(player.level(), hit.getBlockPos()).isWorld()) continue;
            if (space.toWorld(hit.getLocation()).distanceTo(from) < reach - .05) return false;
        }
        return true;
    }
    private static boolean continuous(AbstractClientPlayer player, BlockPos pos) {
        var shape = player.level().getBlockState(pos).getShape(player.level(), pos);
        if (shape.isEmpty()) return false;
        var box = shape.bounds();
        double[] size = {box.getXsize(), box.getYsize(), box.getZsize()};
        int axis = size[1] > size[0] ? 1 : 0;
        if (size[2] > size[axis]) axis = 2;
        if (size[axis] < .75 || size[(axis + 1) % 3] > .3 || size[(axis + 2) % 3] > .3) return false;
        Direction d = axis == 0 ? Direction.EAST : axis == 1 ? Direction.UP : Direction.SOUTH;
        for (Direction side : new Direction[]{d, d.getOpposite()}) {
            BlockPos neighbour = pos.relative(side);
            if (player.level().getBlockState(neighbour).getShape(player.level(), neighbour).isEmpty()) return false;
        }
        return true;
    }
    static boolean valid(AbstractClientPlayer player, IKFrame frame, Contact c, boolean right) {
        if (c == null || !c.space.valid()) return false;
        if (c.rope != null) {
            Vector3f shoulder = new Vector3f(right ? -5 : 5, 2, 0);
            Vector3f target = Body.model(frame, c.world());
            boolean exists = c.rope.valid(), reachable = RopePoseMath.reachable(target, shoulder, right);
            boolean unobstructed = exists && reachable && clear(player, frame.jointWorld(shoulder), c.world(), SubLevels.around(player.level(), player.getBoundingBox().inflate(1.2)));
            if (!unobstructed && strm.emfcompat.core.EMFCompatConfig.getBoolean(TransportGrip.KEY_TRACE, false))
                org.slf4j.LoggerFactory.getLogger("EMFCompatTransport").info("[RopeReject] exists={} reachable={} distance={} target={} right={}", exists, reachable, target.distance(shoulder), target, right);
            return unobstructed;
        }
        var space = c.space.refresh();
        var state = player.level().getBlockState(c.block);
        if (state.isAir() || state.canBeReplaced() || c.outline && !continuous(player, c.block)) return false;
        Vector3f shoulder = new Vector3f(right ? -5 : 5, 2, 0);
        Vec3 end = space.toWorld(c.point), from = frame.jointWorld(shoulder);
        if (!BraceMath.reachable(Body.model(frame, end), shoulder, right, true)) return false;
        // Extend just inside the retained face; the first hit must still be this block.
        Vec3 to = c.point.subtract(c.normal.scale(.015));
        var hit = player.level().clip(new ClipContext(space.toLocal(from), to, c.outline ? ClipContext.Block.OUTLINE : ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(c.block)
                && hit.getLocation().distanceTo(c.point) < .07
                && clear(player, from, end, SubLevels.around(player.level(), player.getBoundingBox().inflate(1.2)));
    }
}
