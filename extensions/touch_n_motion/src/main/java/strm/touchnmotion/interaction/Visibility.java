package strm.touchnmotion.interaction;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Visibility gate shared by nearby-block providers: no reaching through intervening walls. */
public final class Visibility {
    private Visibility() {}

    public static boolean visible(AbstractClientPlayer player, BlockPos target, Vec3 point) {
        // The eye is logical (this tick), the contact is rendered (between ticks). Carry the eye
        // through the target's space too, then test each nearby space explicitly. A world clip can
        // return a ship's plot coordinates; never compare those with world distances.
        var targetSpace = SubLevels.at(player.level(), target);
        Vec3 eye = player.getEyePosition();
        if (targetSpace.same(Anchor.space(player))) eye = targetSpace.toWorld(targetSpace.tickToLocal(eye));
        double length = eye.distanceTo(point);
        for (var space : SubLevels.around(player.level(), new net.minecraft.world.phys.AABB(eye, point).inflate(.1))) {
            var hit = space.clip(player.level(), eye, point, ClipContext.Block.COLLIDER, player);
            if (hit.getType() == HitResult.Type.MISS) continue;
            boolean own = space.same(targetSpace) && (hit.getBlockPos().equals(target)
                    || player.level().getBlockState(target).getBlock() instanceof DoorBlock
                    && hit.getBlockPos().equals(target.above()));
            if (!own && space.toWorld(hit.getLocation()).distanceTo(eye) < length - .02) return false;
        }
        return true;
    }
}
