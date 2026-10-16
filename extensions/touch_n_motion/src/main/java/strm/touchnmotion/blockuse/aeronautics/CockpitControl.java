package strm.touchnmotion.blockuse.aeronautics;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A control by the seat that one hand works while the other stays on the wheel: a throttle lever, a
 * typewriter. The cockpit ({@link CockpitControls}) seats the player, chooses the hand, carries it
 * over and back and keeps the contacts on a moving craft; a control only says what is its own -
 * whether it is held, where the hand goes on it, how the torso takes it. A new one is a class of
 * this and a line in {@link CockpitControls#CONTROLS}.
 */
public interface CockpitControl {

    /** The name in the cockpit's decision log: "throttle-R", "typing-L". */
    String name();

    /** Whether this block is this control. */
    boolean matches(BlockState block);

    /** The control the player is working now, or {@code null}: ours from the mod's own state, another player's from what we are told. */
    BlockPos held(AbstractClientPlayer player);

    /** Whether one only aimed at, not yet held, is reached for: a lever is, a keyboard is not. */
    default boolean reachedWhenAimed() {
        return false;
    }

    /** The key held on it now for the typing motion, or -1. */
    default int key(AbstractClientPlayer player) {
        return -1;
    }

    /**
     * Where the hand goes on it now, in the block's own space (on a craft, the plot), or {@code null}:
     * {@code key} the key the typing motion is at (-1 when choosing a hand), {@code right} which hand.
     */
    Vec3 spot(Level level, BlockPos pos, BlockState block, int key, boolean right);

    /** The torso's lean for a hand {@code mix} of the way on it (hand 0 right, 1 left), with the typing {@code effort}; radians, added to {@code lean}. */
    void lean(Vector3f lean, int hand, float mix, float effort);
}
