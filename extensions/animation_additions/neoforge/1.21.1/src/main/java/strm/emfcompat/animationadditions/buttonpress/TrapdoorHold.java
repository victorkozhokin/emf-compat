package strm.emfcompat.animationadditions.buttonpress;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.Category;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.animationadditions.interaction.InteractionProvider;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKMath;
import strm.emfcompat.core.ik.IKResult;
import strm.emfcompat.core.ik.OneBoneIK;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Opening a trapdoor with the hand: looking at one in reach, the player bends to it and the nearer
 * hand goes to its free edge, the other arm and leg going back to balance ({@link ReachPose});
 * opened, the hand goes up with the edge round the hinges and the body straightens, then lets go.
 * Shutting one is the same the other way: the hand on the raised edge goes down with it.
 *
 * <p>Found by the look, not by the body as a lever is: a trapdoor on the floor is under the feet
 * all the time, and walking past it must not bend the player over. Only one the hand opens - not
 * an iron one. Vanilla flips a trapdoor at once; the hand's arc over {@link #SWING_SECONDS} is the
 * motion.</p>
 */
public final class TrapdoorHold implements InteractionProvider {

    public static final TrapdoorHold INSTANCE = new TrapdoorHold();
    public static final String KEY_ENABLED = "trapdoor.enabled";

    /** As a button press: the hand is the click's. */
    private static final int PRIORITY = 10;
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.12, 0.2, 0.05);

    /** Model space: pixels, y down, facing -z. */
    private static final Vector3f RIGHT_SHOULDER = new Vector3f(-5f, 2f, 0f);
    private static final Vector3f LEFT_SHOULDER = new Vector3f(5f, 2f, 0f);
    private static final Vector3f WAIST = new Vector3f(0f, 12f, 0f);
    private static final float ARM = 11f;
    /** Looked for this far along the look, blocks. */
    private static final double RANGE = 3.0;
    /** A trapdoor's edge further than this, in arm lengths from the bent-over shoulder, is left alone. */
    private static final float MAX_REACH = 2.2f;
    /** The torso bends forwards this far at most, and turns towards the hand's side this far, radians. */
    private static final float MAX_BEND = (float) Math.toRadians(55);
    private static final float MAX_TURN = (float) Math.toRadians(20);
    private static final int BEND_STEPS = 11;
    /** A grip less than this many pixels to either side of the middle is taken by the hand already on it. */
    private static final float HAND_KEEP = 1.5f;
    /** The hand's way round the hinges, seconds; then it holds on this long before letting go. */
    private static final double SWING_SECONDS = 0.3;
    private static final double LET_GO_SECONDS = 0.15;
    private static final double BALANCE_SECONDS = 0.2;
    /** Faster than this, blocks per tick, the player walks past trapdoors. */
    private static final double SLOW_BELOW = 0.15;
    /** The panel is 3 pixels thick; the hand takes it this far in from the free edge, blocks. */
    private static final double THICK = 3 / 16.0;
    private static final double EDGE_IN = 1 / 16.0;

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private TrapdoorHold() {
    }

    private static final class State {
        /** The trapdoor held, whether it was open when last seen, and how far open the hand has it, 0..1. */
        BlockPos pos;
        boolean open, moving, right, done;
        float openness;
        long stoppedAt;
        /** The grip in model pixels, the torso turn asked for {pitch, yaw, roll}, and the balance shown. */
        final Vector3f grip = new Vector3f();
        final float[] lean = new float[3];
        float balance;
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Open trapdoors", true,
                "On", "Looking at a trapdoor the player bends to it and takes its edge with the nearer hand, and lifts it open or pulls it shut.",
                "Off", "Leave the arms to EMF.");
    }

    @Override
    public String id() {
        return "TrapdoorHold";
    }

    @Override
    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        long now = context.now();
        double dt = context.dt();
        State state = STATES.seen(player.getUUID(), now).value;
        state.lean[0] = state.lean[1] = state.lean[2] = 0f;
        float bend = 0f;
        try {
            if (!player.onGround() || player.isPassenger() || player.isSleeping() || player.isInWaterOrBubble()
                    || (player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING)) {
                state.pos = null;
                context.decide("off:state");
                return;
            }
            boolean still = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) <= SLOW_BELOW;

            // The one held: flipped since it was last seen, the hand takes it round.
            if (state.pos != null) {
                BlockState block = player.level().getBlockState(state.pos);
                if (!isTarget(block)) {
                    state.pos = null;
                } else if (block.getValue(TrapDoorBlock.OPEN) != state.open) {
                    state.open = !state.open;
                    state.moving = true;
                    state.done = false;
                }
            }
            if (state.moving) {
                float to = state.open ? 1f : 0f;
                float step = (float) (dt / SWING_SECONDS);
                state.openness = Math.abs(to - state.openness) <= step ? to : state.openness + Math.signum(to - state.openness) * step;
                if (state.openness == to) {
                    state.moving = false;
                    state.stoppedAt = now;
                }
            } else if (state.stoppedAt != 0 && (now - state.stoppedAt) / 1e9 > LET_GO_SECONDS) {
                // Done with it: the hand stays off it until the player looks at another.
                state.done = true;
                state.stoppedAt = 0;
            }
            if (!state.moving) {
                BlockPos looked = still ? looked(player) : null;
                if (looked == null || !looked.equals(state.pos)) {
                    state.pos = looked;
                    state.done = false;
                    state.stoppedAt = 0;
                    if (looked != null) {
                        state.open = player.level().getBlockState(looked).getValue(TrapDoorBlock.OPEN);
                        state.openness = state.open ? 1f : 0f;
                    }
                }
            }
            if (state.pos == null || state.done) {
                context.decide(state.done ? "let-go" : "none");
                return;
            }

            BlockState block = player.level().getBlockState(state.pos);
            IKFrame frame = context.frame();
            Vec3 edge = edge(state.pos, block, state.openness);
            Vector3f model = frame.relativeToJoint(edge, new Vector3f());
            // The nearer hand, the right one in the middle; the one already on it keeps it all the
            // way round, and near the middle.
            boolean holding = InteractionRuntime.weight(player.getUUID(),
                    state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, id()) > 1e-3f;
            boolean right = holding && (state.moving || Math.abs(model.x) < HAND_KEEP) ? state.right : model.x < HAND_KEEP;
            Vector3f shoulder = right ? RIGHT_SHOULDER : LEFT_SHOULDER;
            // Bend as far as it takes the hand to the edge, or as far as it goes.
            float side = Math.abs(model.x) < 1.5f ? 0f : model.x < 0 ? 1f : -1f;
            IKResult aim = null;
            float pitch = 0f, yaw = 0f;
            for (int i = 0; i <= BEND_STEPS; i++) {
                float share = i / (float) BEND_STEPS;
                pitch = MAX_BEND * share;
                // Towards -x (the right) is a turn to the right, +yRot.
                yaw = side * MAX_TURN * share;
                aim = OneBoneIK.solveXY(frame, leant(shoulder, pitch, yaw), edge, ARM, 0f, 0f);
                if (aim != null && aim.reach() <= 1f) break;
            }
            if (aim == null || aim.reach() > MAX_REACH) {
                context.decide("out-of-reach");
                return;
            }
            state.grip.set(model);
            state.right = right;
            state.lean[0] = pitch;
            state.lean[1] = yaw;
            bend = pitch / MAX_BEND;
            out.add(Candidate.single(id(), Category.USE, PRIORITY, 1f, TIMING,
                    right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, new float[]{aim.x(), aim.y()}));
            // The click swings the arm; the lift is the swing.
            context.claimArms();
            context.decide((state.moving ? (state.open ? "lift-" : "shut-") : "reach-") + (right ? "R" : "L")
                    + String.format(" bend %.0f", Math.toDegrees(pitch)));
        } finally {
            state.balance += (bend - state.balance)
                    * (bend > state.balance ? Smoothing.fadeIn(dt, BALANCE_SECONDS) : Smoothing.fadeOut(dt, BALANCE_SECONDS));
            if (state.balance < 1e-3f) state.balance = 0f;
        }
    }

    /** The trapdoor under the look, in {@link #RANGE}, that a hand opens; {@code null} when none. */
    private static BlockPos looked(AbstractClientPlayer player) {
        HitResult hit = player.pick(RANGE, 1f, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return null;
        return isTarget(player.level().getBlockState(block.getBlockPos())) ? block.getBlockPos().immutable() : null;
    }

    private static boolean isTarget(BlockState block) {
        return block.getBlock() instanceof TrapDoorBlock && !block.is(Blocks.IRON_TRAPDOOR);
    }

    /**
     * The free edge's middle, {@code openness} of the way round from shut (0) to open (1). The
     * hinges are on the side opposite the facing; shut, the panel lies at the bottom or the top of
     * its block, open it stands against the hinge side - the edge going up from a bottom one, down
     * from a top one.
     */
    private static Vec3 edge(BlockPos pos, BlockState block, float openness) {
        Direction facing = block.getValue(TrapDoorBlock.FACING);
        boolean bottom = block.getValue(TrapDoorBlock.HALF) == Half.BOTTOM;
        Vec3 out = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 hinge = Vec3.atCenterOf(pos).subtract(out.scale(0.5 - THICK / 2))
                .add(0, bottom ? THICK / 2 - 0.5 : 0.5 - THICK / 2, 0);
        double length = 1 - THICK / 2 - EDGE_IN;
        double angle = openness * Math.PI / 2;
        return hinge.add(out.scale(length * Math.cos(angle))).add(0, (bottom ? 1 : -1) * length * Math.sin(angle), 0);
    }

    /** A shoulder pivot carried round the waist by the torso's lean. */
    private static Vector3f leant(Vector3f shoulder, float pitch, float yaw) {
        Quaternionf turn = new Quaternionf().rotationZYX(0f, yaw, pitch);
        return turn.transform(new Vector3f(shoulder).sub(WAIST)).add(WAIST);
    }

    /** The torso turn this asks for, {pitch, yaw, roll}; {@code null} when none. */
    public static float[] torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || state.lean[0] == 0f && state.lean[1] == 0f && state.lean[2] == 0f) return null;
        return state.lean.clone();
    }

    /** The other arm and leg balancing the bend. Called after the pack has animated, before the torso. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || state.balance < 1e-3f) return;
        if (!EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
        ReachPose.balance(parts, state.right, state.balance);
    }

    /**
     * Points the hand on the edge at it from where its shoulder is drawn this frame, after the
     * torso has bent. Called last, after the interaction runtime.
     */
    public static void aimArm(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null) return;
        Effector effector = state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM;
        float w = InteractionRuntime.weight(uuid, effector, INSTANCE.id());
        if (w < 1e-3f) return;
        ModelPart arm = parts.apply(effector.part);
        if (arm == null) return;
        Vector3f to = new Vector3f(state.grip).sub(arm.x, arm.y, arm.z);
        if (to.lengthSquared() < 1e-6f) return;
        to.normalize();
        // As OneBoneIK: the arm hangs along +y.
        float x = -(float) Math.acos(Mth.clamp(to.y, -1f, 1f));
        float y = (float) Math.atan2(-to.x, -to.z);
        arm.xRot += IKMath.wrap(x - arm.xRot) * w;
        arm.yRot += IKMath.wrap(y - arm.yRot) * w;
    }
}
