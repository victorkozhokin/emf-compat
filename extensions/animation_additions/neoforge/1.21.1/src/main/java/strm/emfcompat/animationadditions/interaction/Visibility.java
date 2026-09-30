package strm.emfcompat.animationadditions.interaction;

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
        var hit = player.level().clip(new ClipContext(player.getEyePosition(), point,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target)
                || player.level().getBlockState(target).getBlock() instanceof DoorBlock
                && hit.getBlockPos().equals(target.above());
    }
}
