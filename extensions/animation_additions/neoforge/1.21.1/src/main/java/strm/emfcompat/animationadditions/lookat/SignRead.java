package strm.emfcompat.animationadditions.lookat;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import strm.emfcompat.core.EMFCompatConfig;

/**
 * A sign as a thing to look at, for {@link LookAt}: the sign a player's look rests on close by,
 * and the middle of its board. What the player looks at is found by a ray from the eyes, so it
 * shows for any player, not only one's own.
 */
public final class SignRead {

    public static final String KEY_ENABLED = "lookat.signs";

    /** Blocks: a sign further than this is not read. Seconds: the look rests on it this long first. */
    static final double RANGE = 4.0;
    private static final double REST_SECONDS = 0.25;
    /** What the player looks at is found this often, not every frame. */
    private static final long LOOK_EVERY_NANOS = 100_000_000L;

    /** A player's look on a sign: which, and since when. */
    static final class Rest {
        BlockPos sign;
        long restingSince, lookedAt;
    }

    private SignRead() {
    }

    /** The sign the look has rested on long enough to read it; {@code null} when there is none or signs are not read. */
    static BlockPos resting(AbstractClientPlayer player, Rest rest, long now) {
        if (!EMFCompatConfig.getBoolean(KEY_ENABLED, true)) {
            rest.sign = null;
            return null;
        }
        if (now - rest.lookedAt >= LOOK_EVERY_NANOS) {
            rest.lookedAt = now;
            BlockPos sign = lookedAt(player);
            if (sign == null || !sign.equals(rest.sign)) rest.restingSince = now;
            rest.sign = sign;
        }
        return rest.sign != null && (now - rest.restingSince) / 1e9 >= REST_SECONDS ? rest.sign : null;
    }

    /** The sign the player's look rests on within {@link #RANGE}; {@code null} for anything else. */
    private static BlockPos lookedAt(AbstractClientPlayer player) {
        Vec3 eyes = player.getEyePosition();
        BlockHitResult hit = player.level().clip(new ClipContext(eyes, eyes.add(player.getViewVector(1f).scale(RANGE)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        return player.level().getBlockState(hit.getBlockPos()).getBlock() instanceof SignBlock ? hit.getBlockPos().immutable() : null;
    }

    /** The middle of a sign's board: of its shape, but for a sign on a post - whose shape is the post's too - the board on top. */
    static Vec3 board(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof StandingSignBlock) return new Vec3(pos.getX() + 0.5, pos.getY() + 0.83, pos.getZ() + 0.5);
        VoxelShape shape = state.getShape(level, pos);
        return shape.isEmpty() ? Vec3.atCenterOf(pos) : shape.bounds().getCenter().add(pos.getX(), pos.getY(), pos.getZ());
    }
}
