package strm.touchnmotion.blockuse.vanilla;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import strm.touchnmotion.interaction.SubLevels;
import strm.touchnmotion.interaction.Body;
import strm.touchnmotion.blockuse.*;

/**
 * An enchanting table: looked at, both hands go to the sides of the book over it. The book turns
 * to the player by itself ({@code EnchantingTableBlockEntity.rot}); the hands are on the sides of
 * the book as it is turned now, so they go round with it while it turns to face the player.
 */
public final class EnchantingTable implements BlockTarget {

    /** The book: blocks over the table's bottom (the renderer's 0.75 + 0.1, and half its thickness); a page's width out. */
    private static final double BOOK_Y = 14 / 16.0;
    private static final double PAGE = 5 / 16.0;
    /** Towards the reader, off the spine. */
    private static final double NEAR = 1 / 16.0;

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof EnchantingTableBlock;
    }

    @Override
    public boolean menu() {
        return true;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return side(player, pos, false);
    }

    @Override
    public Spot supportHand(AbstractClientPlayer player, BlockPos pos, BlockState block) {
        return side(player, pos, true);
    }

    @Override
    public Gesture changed(BlockPos pos, Object before, Object now) {
        return null;
    }

    private static Spot side(AbstractClientPlayer player, BlockPos pos, boolean support) {
        Vec3 reader = reader(player, pos);
        // The reader's right: the main hand's side.
        Vec3 right = new Vec3(reader.z, 0, -reader.x);
        boolean positive = Body.right(player, !support);
        Vec3 point = new Vec3(pos.getX() + 0.5, pos.getY() + BOOK_Y, pos.getZ() + 0.5)
                .add(right.scale(positive ? PAGE : -PAGE)).add(reader.scale(NEAR));
        return new Spot(point, Spots.UP);
    }

    /** The way the book is open to, level: where it is turned now; towards the player when it tells nothing. */
    private static Vec3 reader(AbstractClientPlayer player, BlockPos pos) {
        Vec3 to = SubLevels.at(player.level(), pos).toLocal(player.position()).subtract(Vec3.atCenterOf(pos));
        Vec3 toPlayer = new Vec3(to.x, 0, to.z);
        toPlayer = toPlayer.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : toPlayer.normalize();
        if (player.level().getBlockEntity(pos) instanceof EnchantingTableBlockEntity book) {
            Vec3 open = new Vec3(Math.cos(book.rot), 0, Math.sin(book.rot));
            // Turned away - no one was near - the hands would cross: wait for it on the player's side.
            if (open.dot(toPlayer) > 0) return open;
        }
        return toPlayer;
    }
}
