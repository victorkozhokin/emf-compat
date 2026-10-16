package strm.touchnmotion.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import strm.emfcompat.core.EMFCompatConfig;
import strm.touchnmotion.buttonpress.HandleLevers;
import strm.touchnmotion.buttonpress.aeronautics.ThrottleLever;
import strm.touchnmotion.interaction.SubLevels;

/**
 * A debugging aid: a spark on every point the hand takes hold of on the levers around the player -
 * the handles worked out from each mod's model and renderer - so a grip off the handle shows at a
 * glance, without a body in the way. Off unless asked for.
 */
public final class GripMarkers {
    public static final String KEY = "debug.grip_markers";
    private static final int RADIUS = 4;
    private static int ticks;

    private GripMarkers() {
    }

    public static void tick() {
        if (!EMFCompatConfig.getBoolean(KEY, false) || ticks++ % 2 != 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        BlockPos centre = minecraft.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-RADIUS, -2, -RADIUS), centre.offset(RADIUS, 2, RADIUS))) {
            var block = minecraft.level.getBlockState(pos);
            Vec3[] grips = HandleLevers.is(block) ? HandleLevers.grips(minecraft.level, pos)
                    : ThrottleLever.is(block) ? ThrottleLever.grips(minecraft.level, pos) : null;
            if (grips == null) continue;
            for (Vec3 grip : grips) {
                Vec3 at = SubLevels.toWorld(minecraft.level, pos, grip);
                minecraft.level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0, 0, 0);
            }
        }
    }
}
