package strm.emfcompat.animationadditions.blockuse.supplementaries;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.blockuse.*;

/**
 * Supplementaries' crank: looked at, the hand is on the end of its handle, and goes round with it
 * as each click turns it a sixteenth ({@code power} 0..15). Optional, by name.
 *
 * <p>The handle as its models draw it ({@code crank/crank_handle_N}, made facing north): an arm 7
 * px long from the middle (8, 8), 12 px in from the front, straight up at power 0 and turned
 * towards -x by 22.5 degrees a step; the blockstate turns the whole to the block's facing.</p>
 */
public final class SuppCrank implements BlockTarget {

    private static final ModBlock BLOCK = ModBlock.exact("net.mehvahdjukaar.supplementaries.common.block.blocks.CrankBlock", "supplementaries:crank");
    private static final float ARM = 7f, DEPTH = 12f;

    @Override
    public boolean matches(BlockState block) {
        return BLOCK.is(block);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        Property<?> facingProperty = block.getBlock().getStateDefinition().getProperty("facing");
        Property<?> powerProperty = block.getBlock().getStateDefinition().getProperty("power");
        if (facingProperty == null || powerProperty == null) return null;
        if (!(block.getValue(facingProperty) instanceof Direction facing) || !(block.getValue(powerProperty) instanceof Integer power)) return null;
        double angle = Math.toRadians(power * 22.5);
        Vector3f grip = new Vector3f((float) (-ARM * Math.sin(angle)), (float) (ARM * Math.cos(angle)), DEPTH - 8f).div(16f);
        // The blockstate's turn of the model made facing north: about x for up and down, about y otherwise.
        Quaternionf turn = switch (facing) {
            case UP -> new Quaternionf().rotationX((float) Math.toRadians(-270));
            case DOWN -> new Quaternionf().rotationX((float) Math.toRadians(-90));
            case EAST -> new Quaternionf().rotationY((float) Math.toRadians(-90));
            case SOUTH -> new Quaternionf().rotationY((float) Math.toRadians(-180));
            case WEST -> new Quaternionf().rotationY((float) Math.toRadians(-270));
            default -> new Quaternionf();
        };
        turn.transform(grip);
        return new Spot(new Vec3(pos.getX() + 0.5 + grip.x, pos.getY() + 0.5 + grip.y, pos.getZ() + 0.5 + grip.z),
                Vec3.atLowerCornerOf(facing.getNormal()));
    }

    @Override
    public Gesture changed(BlockPos pos, Object before, Object now) {
        return null;
    }
}
