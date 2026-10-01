package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * Simulated's linked typewriter: looked at, both hands are over its keyboard, one on each half,
 * and stay there while the player types on it, wherever they look; a
 * key going down ({@code getPressedKeys}, every client's to see), the main hand strikes the keys.
 * Optional, by name. The keyboard is the low front of the model ({@code linked_typewriter/block},
 * made facing north: z 0..7, the keys' tops at about y 4.5).
 */
final class Typewriter implements BlockTarget {

    private static final String BLOCK = "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlock";
    private static final ModAccess PRESSED = new ModAccess("getPressedKeys");
    /** The keys, pixels in the model: their height, how far from the front, and each hand off the middle. */
    private static final double KEYS_Y = 4.5, KEYS_Z = 3.5, HAND = 3;

    /** Who types on it ({@code currentUser}, sent to every client). */
    private static final ModAccess USER = new ModAccess("currentUser");

    private record Seen(BlockState block, List<?> pressed) {
    }

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock().getClass().getName().equals(BLOCK);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return keys(pos, block, player.getMainArm() == HumanoidArm.RIGHT ? -HAND : HAND);
    }

    @Override
    public Spot supportHand(AbstractClientPlayer player, BlockPos pos, BlockState block) {
        return keys(pos, block, player.getMainArm() == HumanoidArm.RIGHT ? HAND : -HAND);
    }

    /** Typing, the player looks round freely - the keys take the keyboard, not the look: the hands stay on them. */
    @Override
    public boolean holds(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        return player.getUUID().equals(USER.read(level.getBlockEntity(pos)));
    }

    @Override
    public Object snapshot(Level level, BlockPos pos, BlockState block) {
        return new Seen(block, PRESSED.read(level.getBlockEntity(pos)) instanceof List<?> keys ? List.copyOf(keys) : List.of());
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now)) return null;
        // A key gone down, not one let go.
        boolean struck = !now.pressed.isEmpty() && !before.pressed.containsAll(now.pressed);
        return struck ? new Gesture(keys(pos, now.block, 0), Motion.TAP) : null;
    }

    /** A point of the keyboard, {@code side} pixels off its middle - the typist's right is the model's -x. */
    private static Spot keys(BlockPos pos, BlockState block, double side) {
        Direction facing = block.getValue(BlockStateProperties.HORIZONTAL_FACING);
        // The model is made facing north; Spots.turned takes one made facing south.
        return Spots.turned(pos, facing.getOpposite(), 8 + side, KEYS_Y, KEYS_Z);
    }
}
