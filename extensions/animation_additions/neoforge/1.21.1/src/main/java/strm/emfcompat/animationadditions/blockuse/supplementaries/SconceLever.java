package strm.emfcompat.animationadditions.blockuse.supplementaries;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import strm.emfcompat.animationadditions.blockuse.*;

/**
 * Supplementaries' sconce lever: looked at, the hand is on the sconce's cup, and stays on it as a
 * click tips the sconce out from the wall or back ({@code powered}). Optional, by name.
 *
 * <p>The sconce as its models draw it ({@code sconce_wall}, {@code sconce_wall_lever}, made facing
 * north): the cup's front at (8, 10.5, 10); powered, the whole is turned -22.5 degrees about x
 * round (8, 7, 12). The blockstate turns the model to the block's facing.</p>
 */
public final class SconceLever implements BlockTarget {

    private static final String BLOCK = "net.mehvahdjukaar.supplementaries.common.block.blocks.SconceLeverBlock";
    private static final double GRIP_Y = 10.5, GRIP_Z = 10, PIVOT_Y = 7, PIVOT_Z = 12;
    private static final double TIP = Math.toRadians(-22.5);

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock().getClass().getName().equals(BLOCK);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return grip(pos, block);
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof BlockState before) || !(is instanceof BlockState now) || !matches(before) || !matches(now)) return null;
        if (powered(before) == powered(now)) return null;
        Spot grip = grip(pos, now);
        return grip == null ? null : new Gesture(grip, Motion.HOLD);
    }

    private static boolean powered(BlockState block) {
        Property<?> powered = block.getBlock().getStateDefinition().getProperty("powered");
        return powered != null && Boolean.TRUE.equals(block.getValue(powered));
    }

    private static Spot grip(BlockPos pos, BlockState block) {
        Property<?> facingProperty = block.getBlock().getStateDefinition().getProperty("facing");
        if (facingProperty == null || !(block.getValue(facingProperty) instanceof Direction facing)) return null;
        double y = GRIP_Y, z = GRIP_Z;
        if (powered(block)) {
            double dy = GRIP_Y - PIVOT_Y, dz = GRIP_Z - PIVOT_Z;
            y = PIVOT_Y + dy * Math.cos(TIP) - dz * Math.sin(TIP);
            z = PIVOT_Z + dy * Math.sin(TIP) + dz * Math.cos(TIP);
        }
        // Spots.turned is for a model made facing south: one made facing north is that turned half round.
        return new Spot(Spots.turned(pos, facing.getOpposite(), 8, y, z).point(), Vec3.atLowerCornerOf(facing.getNormal()));
    }
}
