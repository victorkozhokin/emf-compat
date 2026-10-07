package strm.touchnmotion.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import strm.touchnmotion.interaction.SubLevels;
import traben.entity_model_features.models.animation.state.EMFState;

/** A player's two hands on a rim that turns - a steering wheel, a valve: which rim, which side, and the regrips. */
public class RimGrip {

    /** A rim not looked at for this long is taken hold of anew. */
    private static final long LEFT_NANOS = 600_000_000L;

    private BlockPos pos;
    private BlockState mount;
    private long at;
    private float frame = -1;
    /** Whether the right hand takes the rim's positive side. */
    public boolean positive;
    public SteeringGripMotion motion = new SteeringGripMotion();

    /** Whether the hands must be laid on anew: another rim, or this one changed or left alone. */
    public boolean left(BlockPos pos, BlockState block, long now) {
        return !pos.equals(this.pos) || !block.equals(mount) || now - at > LEFT_NANOS;
    }

    public void lay(BlockPos pos, BlockState block, boolean positive, SteeringGripMotion motion) {
        this.pos = pos.immutable();
        this.mount = block;
        this.positive = positive;
        this.motion = motion;
        frame = -1;
    }

    /** Follows the rim to {@code radians}, once a drawn frame; hands just laid on take no step. */
    public void turn(float radians, boolean fresh, long now) {
        float counter = EMFState.getFrameCounter();
        if (counter == frame) return;
        motion.advance(radians, fresh ? 0 : Math.min(.1f, (now - at) * 1e-9f));
        at = now;
        frame = counter;
    }

    /** Which side of the rim this hand holds: the right hand the side picked, the left the other. */
    public boolean side(boolean rightHand) {
        return rightHand == positive;
    }

    /**
     * Whether the rim's positive side - {@code side}, seen from its {@code centre}, both in the
     * block's own space - is at the player's right as they face the rim.
     */
    public static boolean positiveSide(AbstractClientPlayer player, BlockPos pos, Vec3 centre, Vec3 side) {
        Vec3 across = SubLevels.at(player.level(), pos).directionToWorld(side.subtract(centre));
        Vec3 toward = SubLevels.toWorld(player.level(), pos, centre).subtract(player.position());
        return WheelGeometry.positiveSide((float) across.x, (float) across.z, (float) toward.x, (float) toward.z);
    }
}
