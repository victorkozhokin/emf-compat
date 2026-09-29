package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A jukebox: the hand goes to the slot on top - with a disc in the hand and none in the jukebox,
 * or to one playing, to take it out - and puts the disc down into it or lifts it out. Looked at on
 * any face, as a click on any face uses it.
 */
final class Jukebox implements BlockTarget {

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof JukeboxBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        boolean record = block.getValue(JukeboxBlock.HAS_RECORD);
        boolean disc = player.getMainHandItem().has(DataComponents.JUKEBOX_PLAYABLE);
        return record || disc ? slot(pos) : null;
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now)) return null;
        if (!matches(before) || before.getValue(JukeboxBlock.HAS_RECORD) == now.getValue(JukeboxBlock.HAS_RECORD)) return null;
        return new Gesture(slot(pos), now.getValue(JukeboxBlock.HAS_RECORD) ? Motion.PUT : Motion.TAKE);
    }

    /** The middle of the top, where the disc goes in. */
    private static Spot slot(BlockPos pos) {
        return new Spot(new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5), new Vec3(0, 1, 0));
    }
}
