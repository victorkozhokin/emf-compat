package strm.emfcompat.animationadditions.blockuse.create;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import strm.emfcompat.animationadditions.blockuse.*;

/**
 * Create's contraption controls, standing in the world: looked at, the hand waits over the button
 * on its sloped top; pressed (its actors switched off or on - the block entity's {@code disabled}),
 * the hand taps it. Optional, by name. The same block carried by a contraption is not in the world
 * and is not found here.
 */
public final class ContraptionControls implements BlockTarget {

    private static final ModBlock BLOCK = ModBlock.exact("com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsBlock", "create:contraption_controls");
    /** The middle of the button's top in the model ({@code contraption_controls/button}, made facing south), pixels. */
    private static final double BUTTON_X = 8, BUTTON_Y = 14.5, BUTTON_Z = 10.3;

    private static Field disabled;
    private static boolean failed;

    private record Seen(BlockState block, boolean disabled) {
    }

    @Override
    public boolean matches(BlockState block) {
        return BLOCK.is(block);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return button(pos, block);
    }

    @Override
    public Object snapshot(Level level, BlockPos pos, BlockState block) {
        return new Seen(block, disabled(level.getBlockEntity(pos)));
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now)) return null;
        return before.disabled != now.disabled ? new Gesture(button(pos, now.block), Motion.TAP) : null;
    }

    private static Spot button(BlockPos pos, BlockState block) {
        return Spots.turned(pos, block.getValue(BlockStateProperties.HORIZONTAL_FACING), BUTTON_X, BUTTON_Y, BUTTON_Z);
    }

    private static boolean disabled(BlockEntity entity) {
        if (entity == null || failed) return false;
        try {
            if (disabled == null) disabled = entity.getClass().getField("disabled");
            return disabled.getBoolean(entity);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            LoggerFactory.getLogger("EMFCompatBlockUse").warn("[BlockUse] cannot read the contraption controls' state", e);
            return false;
        }
    }
}
