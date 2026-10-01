package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Supplementaries' blackboard: looked at on its front, the hand is over the pixel of the board the
 * look is on; a pixel drawn or wiped (what the board holds changes - every client's to see), the
 * hand is down on that pixel, and stays down on the board from pixel to pixel while the drawing
 * goes on - it follows the picture as it is made. Optional, by name.
 */
final class Blackboard implements BlockTarget {

    private static final String BLOCK = "net.mehvahdjukaar.supplementaries.common.block.blocks.BlackboardBlock";
    /** The board's picture ({@code BlackboardData}, replaced whole when a pixel changes). */
    private static final ModAccess DATA = new ModAccess("data");
    private static final double RANGE = 3.0;

    /** What is drawn, and the pixel the player looks at. Equal by what is drawn. */
    private record Seen(BlockState block, Object drawn, Spot looked) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Seen s && s.block == block && Objects.equals(s.drawn, drawn);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(drawn);
        }
    }

    private final Map<UUID, Spot> looked = new HashMap<>();

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock().getClass().getName().equals(BLOCK);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        Property<?> facing = block.getBlock().getStateDefinition().getProperty("facing");
        Direction front = facing != null && block.getValue(facing) instanceof Direction d ? d : null;
        if (front != null && hit.getDirection() != front) {
            looked.remove(player.getUUID());
            return null;
        }
        Vec3 at = hit.getLocation();
        // The middle of the pixel: the board is 16 by 16 of them.
        Vec3 pixel = new Vec3(snap(at.x, hit.getDirection().getAxis() == Direction.Axis.X),
                snap(at.y, hit.getDirection().getAxis() == Direction.Axis.Y),
                snap(at.z, hit.getDirection().getAxis() == Direction.Axis.Z));
        Spot spot = new Spot(pixel, Vec3.atLowerCornerOf(hit.getDirection().getNormal()));
        looked.put(player.getUUID(), spot);
        return spot;
    }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        // The look is not followed while the hand is down on the board: find the pixel under it here.
        HitResult hit = player == Minecraft.getInstance().player && Minecraft.getInstance().hitResult != null
                ? Minecraft.getInstance().hitResult : player.pick(RANGE, 1f, false);
        if (hit instanceof BlockHitResult on && hit.getType() == HitResult.Type.BLOCK && on.getBlockPos().equals(pos)) {
            hover(player, pos, block, on);
        }
        return new Seen(block, DATA.read(level.getBlockEntity(pos)), looked.get(player.getUUID()));
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen) || !(is instanceof Seen now) || now.looked == null) return null;
        return new Gesture(now.looked, Motion.HOLD);
    }

    /** {@code value} at the middle of its sixteenth; left as it is along the board's own depth. */
    private static double snap(double value, boolean depth) {
        return depth ? value : (Math.floor(value * 16) + 0.5) / 16;
    }
}
