package strm.emfcompat.animationadditions.lookat;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.Category;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.animationadditions.interaction.InteractionProvider;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.animationadditions.torso.TorsoLean;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

import java.util.List;
import java.util.UUID;

/**
 * Reading a sign: with the look resting on a sign close by, the head settles on the middle of its
 * board and the body comes round to face it and leans in a little, the way one stops to read.
 *
 * <p>What the player looks at is found by a ray from the eyes, so it shows for any player, not
 * only one's own. The head is an idle look - anything else that wants the head takes it - and the
 * body's share is a hint to {@link TorsoLean} on top of what it already gives a turned head.</p>
 *
 * <p>Kept apart from {@link LookAt} and small: a look with a thing to look at and a say in what
 * the body does about it. Whatever looking is reworked into, this is one more thing to look at.</p>
 */
public final class SignRead implements InteractionProvider {

    public static final SignRead INSTANCE = new SignRead();
    public static final String KEY_ENABLED = "lookat.signs";

    private static final Candidate.Timing TIMING = new Candidate.Timing(0.25, 0.2, 0.06);
    /** Blocks: a sign further than this is not read. Seconds: the look rests on it this long first. */
    private static final double RANGE = 4.0, REST_SECONDS = 0.25;
    /** What the player looks at is found this often, not every frame. */
    private static final long LOOK_EVERY_NANOS = 100_000_000L;
    /** Radians: the lean in towards a sign at arm's length; the share of the head's turn the body takes on top of its usual, and its limit. */
    private static final float LEAN = 0.1f, TURN_SHARE = 0.35f, TURN_LIMIT = 0.35f;

    private static final class State {
        BlockPos sign;
        long restingSince, lookedAt;
        float reading, yaw, near;
    }

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private SignRead() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Read signs", true,
                "On", "Looking at a sign close by, the head settles on it and the body turns to face it and leans in.",
                "Off", "A sign is looked at like anything else.");
    }

    @Override
    public String id() {
        return "SignRead";
    }

    @Override
    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        long now = context.now();
        State state = STATES.seen(player.getUUID(), now).value;
        if (now - state.lookedAt >= LOOK_EVERY_NANOS) {
            state.lookedAt = now;
            BlockPos sign = player.isSleeping() ? null : lookedAt(player);
            if (sign == null || !sign.equals(state.sign)) state.restingSince = now;
            state.sign = sign;
        }
        boolean reading = state.sign != null && (now - state.restingSince) / 1e9 >= REST_SECONDS;
        float[] aim = null;
        if (reading) {
            Vec3 board = board(player.level(), state.sign);
            aim = LookAt.aim(context.frame(), board);
            // Behind the shoulder a sign is not read: the look is on it only by the camera's freedom.
            if (Math.abs(aim[0]) > LookAt.NECK_YAW || Math.abs(aim[1]) > LookAt.NECK_PITCH) {
                aim = null;
            } else {
                state.yaw = aim[0];
                state.near = (float) Mth.clamp(1.0 - (board.distanceTo(player.getEyePosition()) - 1.0) / (RANGE - 1.0), 0.0, 1.0);
            }
        }
        state.reading += ((aim != null ? 1f : 0f) - state.reading) * Smoothing.follow(context.dt(), 0.2);
        if (aim == null) {
            context.decide(state.sign == null ? "none" : reading ? "behind" : "resting");
            return;
        }
        // The head part takes {xRot, yRot}: pitch, then yaw.
        out.add(Candidate.single(id(), Category.IDLE, 20, 1f, TIMING, Effector.HEAD, new float[]{aim[1], aim[0]}));
        context.decide("read " + state.sign.toShortString());
    }

    /** What reading asks of the torso: round towards the sign, and in towards it the nearer it is; {@code null} when no sign is read. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || state.reading < 1e-3f || !INSTANCE.isEnabled()) return null;
        return TorsoLean.Hint.turn(LEAN * state.near * state.reading,
                Mth.clamp(state.yaw * TURN_SHARE, -TURN_LIMIT, TURN_LIMIT) * state.reading, 0f);
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
    private static Vec3 board(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof StandingSignBlock) return new Vec3(pos.getX() + 0.5, pos.getY() + 0.83, pos.getZ() + 0.5);
        VoxelShape shape = state.getShape(level, pos);
        return shape.isEmpty() ? Vec3.atCenterOf(pos) : shape.bounds().getCenter().add(pos.getX(), pos.getY(), pos.getZ());
    }
}
