package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

/** A note block: the hand waits over the top and pats it when a click tunes it (the note changes). */
final class NoteBlock implements BlockTarget {

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof net.minecraft.world.level.block.NoteBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return Spots.top(pos, 8, 16, 8);
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now) || !matches(before) || !matches(now)) return null;
        return !before.getValue(BlockStateProperties.NOTE).equals(now.getValue(BlockStateProperties.NOTE))
                ? new Gesture(Spots.top(pos, 8, 16, 8), Motion.TAP) : null;
    }
}
