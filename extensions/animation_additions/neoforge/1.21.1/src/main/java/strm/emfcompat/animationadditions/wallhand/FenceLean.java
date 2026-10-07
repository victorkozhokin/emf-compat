package strm.emfcompat.animationadditions.wallhand;

import static strm.emfcompat.animationadditions.interaction.Skeleton.LEFT_SHOULDER;
import static strm.emfcompat.animationadditions.interaction.Skeleton.RIGHT_SHOULDER;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Body;
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.animationadditions.interaction.InteractionProvider;
import strm.emfcompat.animationadditions.interaction.Skeleton;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.animationadditions.ride.Riders;
import strm.emfcompat.animationadditions.torso.BraceSteps;
import strm.emfcompat.animationadditions.torso.PelvisFollow;
import strm.emfcompat.animationadditions.torso.TorsoLean;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.ik.IKFrame;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Leaning on a fence: stood up against a fence or a wall and left standing a moment, the player
 * lays both hands on its top, out to either side, and leans forward on them - the way one stops at
 * a rail to look over it. A step, a turn away, a swing or an item in use, and they stand up again.
 *
 * <p>An arm does not bend, so the hands are not laid straight ahead: each goes to the place along
 * the top an arm's length from its shoulder as the shoulder is once the body has leant, out to its
 * own side - and only where there is fence under it. The fists are then kept on those places by
 * the last fit the riders use ({@link Riders#grip}).</p>
 */
public final class FenceLean implements InteractionProvider {

    public static final FenceLean INSTANCE = new FenceLean();
    public static final String KEY_ENABLED = "fencelean.enabled";

    /** Over a hand laid on a wall: the fence is a wall to it. */
    private static final int PRIORITY = 30;
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.3, 0.05, 0.06);
    /** Seconds stood still before the lean; the lean coming on and off. */
    private static final double REST_SECONDS = 0.6, EASE_SECONDS = 0.3;
    /** Radians: how far forward the body leans on its hands. */
    private static final float LEAN = 0.26f;
    /** Blocks: how near the fence's middle line the player stands to lean on it - up against it, no further. */
    private static final double NEAREST = 0.3, FURTHEST = 0.62;
    /** The fence is before the player within this of square on; cosine. */
    private static final double SQUARE = Math.cos(Math.toRadians(35));
    /** Blocks: the palm over the top it lies on, and as far out to its side as a hand is laid. */
    private static final double OVER_TOP = 0.03, WIDEST = 0.6;
    /** Model pixels from where the pack has the soles (back is +z, the right is -x): the right foot stays all but under the body, the left is set back; and the hips' way back. */
    private static final Vector3f RIGHT_FOOT = new Vector3f(-0.4f, 0f, -0.6f), LEFT_FOOT = new Vector3f(0.9f, 0f, 3.2f), HOME = new Vector3f();
    private static final float HIPS_BACK = 1.4f;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("EMFCompatLean");
    /** The fence before the player is looked for this often, not every frame. */
    private static final long LOOK_EVERY_NANOS = 150_000_000L;

    /** A fence's top before the player: a point on its middle line, the way along it, the way to it, and its height. */
    private record Rail(Vec3 on, Vec3 along, Vec3 towards, double top) {
    }

    private static final class State {
        Rail rail;
        long lookedAt, restingSince;
        float leaning;
        boolean held;
        final Vector3f[] place = {new Vector3f(), new Vector3f()};
        final float[] gap = new float[2];
        AbstractClientPlayer player;
        IKFrame frame;
        final BraceSteps.State stance = new BraceSteps.State();
    }

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private FenceLean() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Lean on a fence", true,
                "On", "Stood up against a fence or a wall for a moment, the player lays both hands on its top and leans on them.",
                "Off", "Stand at a fence like anywhere else.");
    }

    @Override
    public String id() {
        return "FenceLean";
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
        boolean still = Body.planted(player) && player.getPose() == Pose.STANDING && !player.swinging && !player.isUsingItem()
                && !player.isSleeping();
        if (now - state.lookedAt >= LOOK_EVERY_NANOS) {
            state.lookedAt = now;
            state.rail = still ? rail(player) : null;
        }
        if (!still || state.rail == null) state.restingSince = now;
        if (still && state.rail != null) {
            // Square on to the fence: the hands are laid from the shoulders, and a body askew would lay them askew.
            float square = (float) Math.toDegrees(Math.atan2(-state.rail.towards.x, state.rail.towards.z));
            player.yBodyRot += net.minecraft.util.Mth.wrapDegrees(square - player.yBodyRot) * Smoothing.follow(context.dt(), 0.15);
            player.yBodyRotO = player.yBodyRot;
        }
        state.player = player;
        state.frame = context.frame();
        boolean lean = still && state.rail != null && (now - state.restingSince) / 1e9 >= REST_SECONDS;
        Vec3[] grips = lean ? grips(context.frame(), state.rail, player.level()) : null;
        state.held = grips != null && Riders.hold(out, id(), PRIORITY, TIMING, context.frame(), grips[0], grips[1], 1.5f);
        state.leaning += ((state.held ? 1f : 0f) - state.leaning) * Smoothing.follow(context.dt(), EASE_SECONDS);
        if (state.held) {
            for (int hand = 0; hand < 2; hand++) state.place[hand].set(Body.model(context.frame(), grips[hand]));
        } else if (!still) {
            // A step away and the lean is over at once: nothing of it is carried along.
            state.leaning = 0;
        }
        context.decide(state.held ? "lean" : !still ? "off:moving" : state.rail == null ? "none" : lean ? "no-place" : "resting");
    }

    /** What the lean asks of the torso; {@code null} away from a fence. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || state.leaning < 1e-3f || !INSTANCE.isEnabled()) return null;
        return TorsoLean.Hint.turn(LEAN * state.leaning, 0f, 0f);
    }

    /**
     * The feet, before the torso: the weight goes forward onto the hands, so one foot is set back
     * and a little out - one short step, held through the lean, and back under the body when it
     * ends - and the hips go back from the fence as the chest goes over it.
     */
    public static void support(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || state.player == null || !INSTANCE.isEnabled()) return;
        if (state.leaning < 0.05f && state.stance.resting()) return;
        boolean apart = state.held;
        BraceSteps.apply(state.stance, state.player, state.frame, parts, apart ? RIGHT_FOOT : HOME, apart ? LEFT_FOOT : HOME,
                state.leaning, 0f, LOGGER, "FenceStance");
        PelvisFollow.shift(parts, 0f, HIPS_BACK * state.leaning);
    }

    /** The last word on the hands: each fist kept on its place on the fence's top. */
    public static void grip(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.held || !INSTANCE.isEnabled()) return;
        Riders.grip(uuid, parts, INSTANCE.id(), state.place, state.gap);
    }

    /** The fence the player stands up against, square on to it; {@code null} when there is none. */
    private static Rail rail(AbstractClientPlayer player) {
        // By where the player looks: the body is often a good way round from that, standing still, and is brought square
        // to the fence as the lean begins.
        double yaw = Math.toRadians(player.getYRot());
        Vec3 facing = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Direction to = Direction.getNearest(facing.x, 0, facing.z);
        Vec3 towards = new Vec3(to.getStepX(), 0, to.getStepZ());
        if (facing.dot(towards) < SQUARE) return null;
        Level level = player.level();
        Vec3 feet = player.position();
        BlockPos pos = BlockPos.containing(feet.add(towards.scale(0.6)).add(0, 0.2, 0));
        BlockState block = level.getBlockState(pos);
        if (!leanable(block)) return null;
        Vec3 on = new Vec3(pos.getX() + 0.5, 0, pos.getZ() + 0.5);
        double off = on.subtract(feet).dot(towards);
        if (off < NEAREST || off > FURTHEST) return null;
        return new Rail(on, new Vec3(-towards.z, 0, towards.x), towards, top(level, pos, block));
    }

    private static boolean leanable(BlockState block) {
        return block.getBlock() instanceof FenceBlock || block.getBlock() instanceof WallBlock
                || block.getBlock() instanceof FenceGateBlock && !block.getValue(FenceGateBlock.OPEN);
    }

    /** The top of a fence as it is drawn: a fence stops a walker half a block higher than it stands. */
    private static double top(Level level, BlockPos pos, BlockState block) {
        VoxelShape shape = block.getShape(level, pos);
        return pos.getY() + (shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y));
    }

    /**
     * Where the right hand and the left lie on the fence's top, in the world: an arm's length from
     * each shoulder as it is with the body leant, out to that hand's side; {@code null} when a hand
     * has no such place - the fence too far or too low, or no fence under it.
     */
    private static Vec3[] grips(IKFrame frame, Rail rail, Level level) {
        Vec3 origin = frame.jointWorld(new Vector3f());
        // The model's right is -x.
        Vec3 right = frame.jointWorld(new Vector3f(-1f, 0f, 0f)).subtract(origin);
        double sign = right.dot(rail.along) >= 0 ? 1 : -1;
        double reach = Riders.FIST * Skeleton.SCALE / 16.0;
        Vec3[] grips = new Vec3[2];
        for (int hand = 0; hand < 2; hand++) {
            Vec3 shoulder = frame.jointWorld(Riders.shoulder(hand == 0 ? RIGHT_SHOULDER.x : LEFT_SHOULDER.x, -LEAN, 0f));
            double before = rail.on.subtract(shoulder).dot(rail.towards), down = shoulder.y - (rail.top + OVER_TOP);
            double out = reach * reach - before * before - down * down;
            if (before < 0 || down < 0 || out < 0) {
                if (strm.emfcompat.animationadditions.DebugLog.trace()) org.slf4j.LoggerFactory.getLogger("EMFCompatLean").info("[LeanTrace] hand={} before={} down={} out={} shoulder={} top={}", hand, before, down, out, shoulder, rail.top);
                return null;
            }
            double side = (hand == 0 ? 1 : -1) * sign * Math.min(Math.sqrt(out), WIDEST);
            Vec3 grip = shoulder.add(rail.towards.scale(before)).add(rail.along.scale(side));
            grip = new Vec3(grip.x, rail.top + OVER_TOP, grip.z);
            BlockPos under = BlockPos.containing(grip.x, rail.top - 0.5, grip.z);
            BlockState block = level.getBlockState(under);
            // A wall's run is an eighth lower than its posts: each hand lies at the height of what is under it.
            double here = leanable(block) ? top(level, under, block) : Double.NaN;
            if (!(Math.abs(here - rail.top) <= 0.2)) {
                if (strm.emfcompat.animationadditions.DebugLog.trace()) org.slf4j.LoggerFactory.getLogger("EMFCompatLean").info("[LeanTrace] hand={} under={} block={} grip={} top={}", hand, under.toShortString(), block.getBlock(), grip, rail.top);
                return null;
            }
            grips[hand] = new Vec3(grip.x, here + OVER_TOP, grip.z);
        }
        return grips;
    }
}
