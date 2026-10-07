package strm.emfcompat.animationadditions.blockuse.vanilla;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import strm.emfcompat.animationadditions.blockuse.*;

/**
 * A respawn anchor: with glowstone in the hand and room for more charge, the hand goes to the
 * middle of the top and puts the glowstone in as the charge goes up.
 */
public final class RespawnAnchor implements BlockTarget {

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof RespawnAnchorBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        boolean room = block.getValue(RespawnAnchorBlock.CHARGE) < RespawnAnchorBlock.MAX_CHARGES;
        return room && player.getMainHandItem().is(Items.GLOWSTONE) ? middle(pos) : null;
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now) || !matches(before) || !matches(now)) return null;
        return now.getValue(RespawnAnchorBlock.CHARGE) > before.getValue(RespawnAnchorBlock.CHARGE)
                ? new Gesture(middle(pos), Motion.PUT) : null;
    }

    /** The middle of the top, where the glowstone shows. */
    private static Spot middle(BlockPos pos) {
        return Spots.top(pos, 8, 16, 8);
    }
}
