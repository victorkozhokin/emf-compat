package strm.touchnmotion.blockuse.aeronautics;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Aeronautics' linked typewriter by the seat: one hand over the whole keyboard, leaning into the keys it presses. */
final class TypewriterControl implements CockpitControl {
    private static final Typewriter TYPEWRITER = new Typewriter();

    public String name() { return "typing"; }

    public boolean matches(BlockState block) { return TYPEWRITER.matches(block); }

    public BlockPos held(AbstractClientPlayer player) {
        BlockPos pos = Typewriter.activePosition(player);
        return pos != null && matches(player.level().getBlockState(pos)) ? pos : null;
    }

    public int key(AbstractClientPlayer player) { return Typewriter.pressedKey(player); }

    public Vec3 spot(Level level, BlockPos pos, BlockState block, int key, boolean right) {
        return matches(block) ? Typewriter.cockpitSpot(pos, block, key, right).point() : null;
    }

    public void lean(Vector3f lean, int hand, float mix, float effort) {
        lean.y += (hand == 0 ? 1 : -1) * (float) Math.toRadians(10) * mix;
        lean.z += (hand == 0 ? -1 : 1) * (float) Math.toRadians(3 + 5 * effort) * mix;
        lean.x += (float) Math.toRadians(5) * effort * mix;
    }
}
