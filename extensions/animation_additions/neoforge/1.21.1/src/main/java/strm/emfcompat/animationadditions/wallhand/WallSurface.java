package strm.emfcompat.animationadditions.wallhand;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import strm.emfcompat.animationadditions.interaction.SubLevels;
import java.util.List;

/** Query actual vertical collision faces in the world and nearby Sable sub-levels. */
final class WallSurface {
    record Hit(Vec3 position, Vec3 normal) {}

    static Hit clip(AbstractClientPlayer player, Vec3 from, Vec3 to, List<SubLevels.Space> spaces) {
        Hit best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (SubLevels.Space space : spaces) {
            var hit = player.level().clip(new ClipContext(space.toLocal(from), space.toLocal(to),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS || hit.isInside()) continue;
            Vec3 normal = space.directionToWorld(Vec3.atLowerCornerOf(hit.getDirection().getNormal()));
            if (Math.abs(normal.y) > 0.5) continue;
            Vec3 point = space.toWorld(hit.getLocation());
            double d = from.distanceToSqr(point);
            if (d < distance) { distance = d; best = new Hit(point, normal.normalize()); }
        }
        return best;
    }
}
