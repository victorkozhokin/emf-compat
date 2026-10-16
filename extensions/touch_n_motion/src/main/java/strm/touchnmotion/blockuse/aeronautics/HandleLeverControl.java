package strm.touchnmotion.blockuse.aeronautics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.touchnmotion.buttonpress.HandleLevers;

/**
 * A lever with a handle by the seat - Create's analog lever, Design n' Decor's stepped lever and
 * breaker switch, Compact Gearbox's controller: reached for when aimed at, held while Lever drugster
 * drags it, the hand on the handle as it is drawn. The torso takes it as it takes a throttle.
 */
final class HandleLeverControl implements CockpitControl {
    public String name() { return "lever"; }

    public boolean matches(BlockState block) { return HandleLevers.is(block); }

    public BlockPos held(AbstractClientPlayer player) {
        if (player == Minecraft.getInstance().player) return HandleLevers.dragged();
        BlockPos told = strm.touchnmotion.net.Inputs.throttle(player);
        return told != null && matches(player.level().getBlockState(told)) ? told : null;
    }

    public boolean reachedWhenAimed() { return true; }

    public Vec3 spot(Level level, BlockPos pos, BlockState block, int key, boolean right) {
        return HandleLevers.knob(level, pos);
    }

    public void lean(Vector3f lean, int hand, float mix, float effort) {
        lean.y += (hand == 0 ? 1 : -1) * (float) Math.toRadians(10) * mix;
        lean.z += (hand == 0 ? -1 : 1) * (float) Math.toRadians(3) * mix;
    }
}
