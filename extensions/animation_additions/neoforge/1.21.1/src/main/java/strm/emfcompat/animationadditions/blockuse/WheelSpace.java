package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;

/** Convert optional Sable plot coordinates with the same interpolated pose used by its renderer. */
final class WheelSpace {
    private static boolean looked;
    private static Object helper;
    private static Method containing, renderPose, transform;
    private WheelSpace() {}

    static Vec3 world(Level level, BlockPos pos, Vec3 point) {
        try {
            if (!looked) {
                looked = true;
                try {
                    helper = Class.forName("dev.ryanhcode.sable.Sable").getField("HELPER").get(null);
                    containing = helper.getClass().getMethod("getContaining", Level.class, Vec3i.class);
                    renderPose = Class.forName("dev.ryanhcode.sable.sublevel.ClientSubLevel").getMethod("renderPose", float.class);
                    transform = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc").getMethod("transformPosition", Vec3.class);
                } catch (ClassNotFoundException absent) {
                    helper = null;
                }
            }
            if (helper == null) return point;
            Object sublevel = containing.invoke(helper, level, pos);
            if (sublevel == null) return point;
            Object pose = renderPose.invoke(sublevel, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
            return (Vec3) transform.invoke(pose, point);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot transform wheel from Sable plot to render world", e);
        }
    }

    static BlockTarget.Spot spot(Level level, BlockPos pos, Vec3 point, Vec3 normal) {
        Vec3 at = world(level, pos, point);
        return new BlockTarget.Spot(at, world(level, pos, point.add(normal)).subtract(at).normalize());
    }
}
