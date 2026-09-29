package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A trial chambers vault: with the key it takes in the hand - a trial key, an ominous one for an
 * ominous vault - and the vault active, the key is held to the keyhole in its front, its bit
 * pointing in; when the vault starts unlocking, the key goes in and the hand turns it.
 *
 * <p>Whether this player has opened this vault already is the server's to know: the hand goes to
 * any active vault the key fits.</p>
 */
final class Vault implements BlockTarget {

    /** The keyhole on the front, blocks up from the bottom. */
    private static final double KEYHOLE_Y = 0.45;
    /**
     * The key in the hand, in the arm's axes, pixels: a flat item held as vanilla holds one, its
     * bow at the fist and its bit this far on, forwards out of the fist.
     */
    private static final float[] KEY = {9.5f, -1.5f, 10.5f, -7f};

    @Override
    public float[] item() {
        return KEY;
    }

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof VaultBlock;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        if (block.getValue(VaultBlock.STATE) != VaultState.ACTIVE) return null;
        boolean ominous = block.getValue(VaultBlock.OMINOUS);
        if (!player.getMainHandItem().is(ominous ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY)) return null;
        return keyhole(pos, block);
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now) || !matches(before)) return null;
        boolean unlocked = before.getValue(VaultBlock.STATE) != VaultState.UNLOCKING
                && now.getValue(VaultBlock.STATE) == VaultState.UNLOCKING;
        return unlocked ? new Gesture(keyhole(pos, now), Motion.TURN) : null;
    }

    /** The keyhole: the middle of the front, a little low. */
    private static Spot keyhole(BlockPos pos, BlockState block) {
        Direction facing = block.getValue(VaultBlock.FACING);
        Vec3 out = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 point = new Vec3(pos.getX() + 0.5, pos.getY() + KEYHOLE_Y, pos.getZ() + 0.5).add(out.scale(0.5));
        return new Spot(point, out);
    }
}
