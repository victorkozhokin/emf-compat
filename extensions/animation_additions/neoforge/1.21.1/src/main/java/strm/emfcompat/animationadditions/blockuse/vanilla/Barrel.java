package strm.emfcompat.animationadditions.blockuse.vanilla;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import strm.emfcompat.animationadditions.blockuse.*;

/** A barrel's lid, on any of its six orientations; opening is a short push at the lid. */
public final class Barrel implements BlockTarget {
    public boolean matches(BlockState block) { return block.getBlock() instanceof BarrelBlock; }

    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return hit.getDirection() == block.getValue(BarrelBlock.FACING) ? lid(pos, block) : null;
    }

    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now)
                || !matches(before) || !matches(now)) return null;
        return !before.getValue(BarrelBlock.OPEN) && now.getValue(BarrelBlock.OPEN)
                ? new Gesture(lid(pos, now), Motion.TAP) : null;
    }

    private static Spot lid(BlockPos pos, BlockState block) {
        Vec3 out = Vec3.atLowerCornerOf(block.getValue(BarrelBlock.FACING).getNormal());
        return new Spot(Vec3.atCenterOf(pos).add(out.scale(0.5)), out);
    }
}
