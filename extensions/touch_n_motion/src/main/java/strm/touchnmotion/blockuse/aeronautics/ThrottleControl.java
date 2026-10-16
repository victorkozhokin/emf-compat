package strm.touchnmotion.blockuse.aeronautics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.touchnmotion.buttonpress.aeronautics.ThrottleLever;

/** Aeronautics' throttle lever by the seat: the hand on its knob, going along as it is dragged. */
final class ThrottleControl implements CockpitControl {
    public String name() { return "throttle"; }

    public boolean matches(BlockState block) { return ThrottleLever.is(block); }

    public BlockPos held(AbstractClientPlayer player) {
        return player == Minecraft.getInstance().player ? ThrottleLever.heldPosition() : strm.touchnmotion.net.Inputs.throttle(player);
    }

    public boolean reachedWhenAimed() { return true; }

    public Vec3 spot(Level level, BlockPos pos, BlockState block, int key, boolean right) {
        return ThrottleLever.knob(level, pos);
    }

    public void lean(Vector3f lean, int hand, float mix, float effort) {
        lean.y += (hand == 0 ? 1 : -1) * (float) Math.toRadians(10) * mix;
        lean.z += (hand == 0 ? -1 : 1) * (float) Math.toRadians(3) * mix;
    }
}
