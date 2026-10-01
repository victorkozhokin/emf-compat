package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import strm.emfcompat.animationadditions.interaction.SubLevels;

import java.lang.reflect.Method;

/**
 * Supplementaries' globe: looked at, the hand is at the side of the ball towards the player; spun,
 * it pushes the ball round - across its surface the way the ball goes. Optional, by name.
 *
 * <p>The ball is an 8 px cube about (8, 9, 8). A use adds 450 to the block entity's rotation
 * ({@code spin}), which then runs down to 0; it is drawn turned by {@code 90 - rotation} about +y,
 * so it turns from +z towards +x, and a point of it {@code r} off the axis moves along
 * {@code (r.z, 0, -r.x)}.</p>
 */
final class Globe implements BlockTarget {

    private static final String BLOCK = "net.mehvahdjukaar.supplementaries.common.block.blocks.GlobeBlock";
    /** The ball's middle, blocks up; its half; how long the push is across it, blocks. */
    private static final double CENTRE_Y = 9 / 16.0;
    private static final double RADIUS = 4 / 16.0;
    private static final double PUSH = 7 / 16.0;
    /** The rotation jumps by 450 on a spin and only falls otherwise: this much up is a spin. */
    private static final float SPUN = 90f;

    private static Method rotation;
    private static boolean failed;

    /** How far the ball is turned, and the side of it the player is at. */
    private record Seen(BlockState block, float rotation, Vec3 side) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Seen s && s.block == block && s.rotation == rotation;
        }

        @Override
        public int hashCode() {
            return Float.hashCode(rotation);
        }
    }

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock().getClass().getName().equals(BLOCK);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return at(pos, side(player, pos));
    }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        return new Seen(block, rotation(level, pos), side(player, pos));
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now) || now.rotation < before.rotation + SPUN) return null;
        // The way the ball's surface goes under the hand.
        Vec3 along = new Vec3(now.side.z, 0, -now.side.x).scale(PUSH);
        return new Gesture(at(pos, now.side), Motion.TAP, along);
    }

    private static Spot at(BlockPos pos, Vec3 side) {
        return new Spot(new Vec3(pos.getX() + 0.5, pos.getY() + CENTRE_Y, pos.getZ() + 0.5).add(side.scale(RADIUS)), side);
    }

    /** From the ball towards the player, level. */
    private static Vec3 side(AbstractClientPlayer player, BlockPos pos) {
        Vec3 to = SubLevels.at(player.level(), pos).toLocal(player.position()).subtract(Vec3.atCenterOf(pos));
        Vec3 level = new Vec3(to.x, 0, to.z);
        return level.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : level.normalize();
    }

    private static float rotation(Level level, BlockPos pos) {
        Object entity = level.getBlockEntity(pos);
        if (entity == null || failed) return 0f;
        try {
            if (rotation == null) rotation = entity.getClass().getMethod("getRotation", float.class);
            return (float) rotation.invoke(entity, 1f);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            return 0f;
        }
    }
}
