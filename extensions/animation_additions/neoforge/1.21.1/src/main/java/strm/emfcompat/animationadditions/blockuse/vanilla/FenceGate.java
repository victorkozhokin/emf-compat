package strm.emfcompat.animationadditions.blockuse.vanilla;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import strm.emfcompat.animationadditions.blockuse.*;

/**
 * A fence gate: looked at, the hand waits on the middle of its top rail, on the player's side;
 * opened or shut, it pushes there. The gate turns open about its ends, so the middle is where the
 * hand meets it shut.
 */
public final class FenceGate implements BlockTarget {

    /** The top rail's middle, blocks up; half the gate's thickness, blocks. */
    private static final double RAIL_Y = 13.5 / 16;
    private static final double HALF = 1.0 / 16;

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof FenceGateBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        Direction facing = block.getValue(FenceGateBlock.FACING);
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 toPlayer = player.position().subtract(Vec3.atCenterOf(pos));
        return rail(pos, toPlayer.dot(normal) >= 0 ? normal : normal.scale(-1));
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now) || !matches(before) || !matches(now)) return null;
        if (before.getValue(FenceGateBlock.OPEN) == now.getValue(FenceGateBlock.OPEN)) return null;
        // It opens away from whoever opened it: their side is the one opposite the way it faces now.
        Vec3 normal = Vec3.atLowerCornerOf(now.getValue(FenceGateBlock.FACING).getNormal());
        return new Gesture(rail(pos, normal.scale(-1)), Motion.TAP);
    }

    private static Spot rail(BlockPos pos, Vec3 out) {
        return new Spot(new Vec3(pos.getX() + 0.5, pos.getY() + RAIL_Y, pos.getZ() + 0.5).add(out.scale(HALF)), out);
    }
}
