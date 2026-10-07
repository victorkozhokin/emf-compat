package strm.emfcompat.animationadditions.blockuse.vanilla;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import strm.emfcompat.animationadditions.blockuse.*;

/**
 * A bell: looked at, the hand waits at the side of its body facing the player; rung, the hand
 * strikes it on the side it was struck from ({@code BellBlockEntity.clickDirection}). A ring is the
 * bell starting to shake, or starting over while it still shakes - for anyone's ringing.
 */
public final class Bell implements BlockTarget {

    /** The middle of the bell's body, blocks up, and how far out its side is, blocks. */
    private static final double BODY_Y = 9.5 / 16;
    private static final double BODY_HALF = 3.5 / 16;

    private record Seen(BlockState block, boolean shaking, int ticks, Direction side) {
    }

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof BellBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        Direction side = hit.getDirection().getAxis().isHorizontal() ? hit.getDirection()
                : Direction.getNearest(player.getX() - (pos.getX() + 0.5), 0, player.getZ() - (pos.getZ() + 0.5));
        return side(pos, side);
    }

    @Override
    public Object snapshot(Level level, BlockPos pos, BlockState block) {
        if (level.getBlockEntity(pos) instanceof BellBlockEntity bell) {
            return new Seen(block, bell.shaking, bell.ticks, bell.clickDirection);
        }
        return new Seen(block, false, 0, null);
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now) || !now.shaking) return null;
        boolean rung = !before.shaking || now.ticks < before.ticks;
        if (!rung || now.side == null || !now.side.getAxis().isHorizontal()) return null;
        return new Gesture(side(pos, now.side), Motion.TAP);
    }

    /** The side of the body towards {@code side}. */
    private static Spot side(BlockPos pos, Direction side) {
        Vec3 out = Vec3.atLowerCornerOf(side.getNormal());
        Vec3 point = new Vec3(pos.getX() + 0.5, pos.getY() + BODY_Y, pos.getZ() + 0.5).add(out.scale(BODY_HALF));
        return new Spot(point, out);
    }
}
