package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import strm.emfcompat.animationadditions.interaction.SubLevels;
import strm.emfcompat.animationadditions.interaction.Body;

/**
 * A cartography table: looked at, the other hand holds the map down on the top, and the main hand
 * is on it; with our own player's screen open the main hand goes over the map, to and fro. Whether
 * another player has the screen open is not sent: their hands rest on the table.
 */
final class CartographyTable implements BlockTarget {

    /** Off the middle of the top, blocks: to the main hand's side and towards the player; the holding hand's. */
    private static final double DRAW_SIDE = 3 / 16.0, DRAW_NEAR = 1 / 16.0;
    private static final double HOLD_SIDE = 4.5 / 16.0, HOLD_NEAR = 3 / 16.0;
    /** The hand going over the map: blocks to either side and to and fro, and how fast, radians a second. */
    private static final double STROKE_SIDE = 2 / 16.0, STROKE_NEAR = 1.5 / 16.0;
    private static final double STROKE_SPEED = 2.6;

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock() instanceof CartographyTableBlock;
    }

    @Override
    public boolean menu() {
        return true;
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        double side = DRAW_SIDE, near = DRAW_NEAR;
        if (player == Minecraft.getInstance().player ? player.containerMenu instanceof CartographyTableMenu
                : strm.emfcompat.animationadditions.net.Inputs.menuAt(player, pos)) {
            double t = System.nanoTime() / 1e9 * STROKE_SPEED;
            side += Math.sin(t) * STROKE_SIDE;
            near += Math.sin(t * 1.7) * STROKE_NEAR;
        }
        return hand(player, pos, false, side, near);
    }

    @Override
    public Spot supportHand(AbstractClientPlayer player, BlockPos pos, BlockState block) {
        return hand(player, pos, true, HOLD_SIDE, HOLD_NEAR);
    }

    @Override
    public Gesture changed(BlockPos pos, Object before, Object now) {
        return null;
    }

    private static Spot hand(AbstractClientPlayer player, BlockPos pos, boolean support, double side, double near) {
        Vec3 to = SubLevels.at(player.level(), pos).toLocal(player.position()).subtract(Vec3.atCenterOf(pos));
        Vec3 toPlayer = new Vec3(to.x, 0, to.z);
        toPlayer = toPlayer.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : toPlayer.normalize();
        // The player's right, facing the table: the main hand's side.
        Vec3 right = new Vec3(toPlayer.z, 0, -toPlayer.x);
        boolean positive = Body.right(player, !support);
        Vec3 point = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5)
                .add(right.scale(positive ? side : -side)).add(toPlayer.scale(near));
        return new Spot(point, Spots.UP);
    }
}
