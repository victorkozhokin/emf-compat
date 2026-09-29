package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.state.BlockState;
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
 * Using a block by hand - a chiseled bookshelf's slot, and so on ({@link BlockTarget}): looking at
 * a spot the hand can use, the main hand goes to it and waits there; when the block changes the way
 * a hand changes it, the hand puts in (in and back out), takes out (from inside, out past the
 * front) or taps, the torso going with it a little.
 *
 * <p>By the look, as a click is: the spot looked at is the one used. The hand only goes where the
 * click would do something, and only within reach of the arm with the torso leant a little - no
 * using a block from across the room. The use itself is instant; the gesture plays out after it,
 * as a lever's hand follows the handle over.</p>
 */
public final class BlockUse implements InteractionProvider {

    public static final BlockUse INSTANCE = new BlockUse();
    public static final String KEY_ENABLED = "blockuse.enabled";

    private static final List<BlockTarget> TARGETS = List.of(new ChiseledShelf());

    /** Below a button press, above doors and chests. */
    private static final int PRIORITY = 8;
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.12, 0.18, 0.05);

    /** Model space: pixels, y down, facing -z. */
    private static final Vector3f RIGHT_SHOULDER = new Vector3f(-5f, 2f, 0f);
    private static final Vector3f LEFT_SHOULDER = new Vector3f(5f, 2f, 0f);
    private static final Vector3f WAIST = new Vector3f(0f, 12f, 0f);
    private static final float ARM = 11f;
    /** Looked for this far along the look, blocks. */
    private static final double RANGE = 3.0;
    /** Past the arm's length, as a share of it, with the most lean: further and the hand does not go. */
    private static final float MAX_REACH = 1.5f;
    private static final float MAX_LEAN_PITCH = (float) Math.toRadians(15);
    private static final float MAX_LEAN_YAW = (float) Math.toRadians(15);
    private static final int LEAN_STEPS = 6;
    /** Faster than this, blocks per tick, the player walks past. */
    private static final double SLOW_BELOW = 0.15;

    /** Pixels out of the block: where the hand waits; how deep a put goes in; from where and to where a take pulls. */
    private static final float HOVER_OUT = 1.5f;
    private static final float PUT_IN = 3f;
    private static final float TAKE_FROM = -2f;
    private static final float TAKE_TO = 5f;
    private static final float TAP_IN = 1.5f;
    private static final double GESTURE_SECONDS = 0.35;
    /** The torso going with the hand: forwards putting in, back pulling out, radians at the gesture's middle. */
    private static final float PUT_LEAN = (float) Math.toRadians(6);
    private static final float TAKE_LEAN = (float) Math.toRadians(-4);
    private static final double GRIP_SECONDS = 0.05;

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private BlockUse() {
    }

    private static final class State {
        /** The block looked at, as it was last seen, and its target. */
        BlockPos pos;
        BlockState last;
        BlockTarget target;
        BlockTarget.Gesture gesture;
        long gestureAt;
        boolean right = true, shown;
        /** The hand's point in model pixels, and the torso turn asked for {pitch, yaw, roll}. */
        final Vector3f grip = new Vector3f();
        final float[] lean = new float[3];
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Use blocks by hand", true,
                "On", "Looking at a block the hand can use - a chiseled bookshelf's slot - the hand goes to it, and puts in or takes out.",
                "Off", "Leave the arms to EMF.");
    }

    @Override
    public String id() {
        return "BlockUse";
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
        state.lean[0] = state.lean[1] = state.lean[2] = 0f;
        boolean shown = false;
        try {
            if (!player.onGround() || player.isPassenger() || player.isSleeping() || player.isInWaterOrBubble()
                    || (player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING)) {
                state.pos = null;
                state.gesture = null;
                context.decide("off:state");
                return;
            }
            // The block held changed the way a hand changes it: the gesture.
            if (state.pos != null) {
                BlockState block = player.level().getBlockState(state.pos);
                if (!block.equals(state.last)) {
                    BlockTarget.Gesture gesture = state.target.matches(block) ? state.target.changed(state.pos, state.last, block) : null;
                    if (gesture != null) {
                        state.gesture = gesture;
                        state.gestureAt = now;
                    }
                    state.last = block;
                }
            }
            double t = state.gesture == null ? 1 : (now - state.gestureAt) / 1e9 / GESTURE_SECONDS;
            if (t >= 1) state.gesture = null;

            BlockTarget.Spot spot;
            float outwards;
            float lean = 0f;
            if (state.gesture != null) {
                spot = state.gesture.spot();
                float s = (float) Math.sin(Math.PI * t);
                switch (state.gesture.motion()) {
                    case PUT -> {
                        outwards = HOVER_OUT - PUT_IN * s;
                        lean = PUT_LEAN * s;
                    }
                    case TAKE -> {
                        float e = (float) (1 - (1 - t) * (1 - t));
                        outwards = TAKE_FROM + (TAKE_TO - TAKE_FROM) * e;
                        lean = TAKE_LEAN * s;
                    }
                    default -> outwards = HOVER_OUT - TAP_IN * s;
                }
            } else {
                boolean still = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) <= SLOW_BELOW;
                spot = still ? look(player, state) : null;
                outwards = HOVER_OUT;
            }
            if (spot == null) {
                context.decide("none");
                return;
            }

            IKFrame frame = context.frame();
            Vec3 point = spot.point().add(spot.out().scale(outwards / 16.0));
            Vector3f model = frame.relativeToJoint(point, new Vector3f());
            // The hand that holds what is used.
            boolean right = player.getMainArm() == HumanoidArm.RIGHT;
            Vector3f shoulder = right ? RIGHT_SHOULDER : LEFT_SHOULDER;
            // The least lean that brings it in reach; towards -x (the right) is a turn to the right, +yRot.
            float yawSign = Math.abs(model.x) < 1.5f ? 0f : model.x < 0 ? 1f : -1f;
            IKResult aim = null;
            float pitch = 0f, yaw = 0f;
            for (int i = 0; i <= LEAN_STEPS; i++) {
                float share = i / (float) LEAN_STEPS;
                pitch = MAX_LEAN_PITCH * share;
                yaw = yawSign * MAX_LEAN_YAW * share;
                aim = OneBoneIK.solveXY(frame, leant(shoulder, pitch, yaw), point, ARM, 0f, 0f);
                if (aim != null && aim.reach() <= 1f) break;
            }
            if (aim == null || aim.reach() > MAX_REACH) {
                context.decide("out-of-reach");
                return;
            }
            Effector effector = right ? Effector.RIGHT_ARM : Effector.LEFT_ARM;
            if (!state.shown || right != state.right || InteractionRuntime.weight(player.getUUID(), effector, id()) < 1e-3f) {
                state.grip.set(model);
            } else {
                state.grip.lerp(model, Smoothing.follow(context.dt(), GRIP_SECONDS));
            }
            state.right = right;
            state.lean[0] = pitch + lean;
            state.lean[1] = yaw;
            shown = true;
            out.add(Candidate.single(id(), Category.USE, PRIORITY, 1f, TIMING, effector, new float[]{aim.x(), aim.y()}));
            // The click swings the arm; the gesture is the swing.
            context.claimArms();
            context.decide((state.gesture == null ? "hover" : state.gesture.motion().name().toLowerCase())
                    + (right ? "-R" : "-L"));
        } finally {
            state.shown = shown;
        }
    }

    /** The spot under the look on a block a hand uses, keeping the block to watch it change; {@code null} when none. */
    private static BlockTarget.Spot look(AbstractClientPlayer player, State state) {
        HitResult hit = player.pick(RANGE, 1f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            state.pos = null;
            return null;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState block = player.level().getBlockState(pos);
        BlockTarget target = null;
        for (BlockTarget each : TARGETS) {
            if (each.matches(block)) {
                target = each;
                break;
            }
        }
        if (target == null) {
            state.pos = null;
            return null;
        }
        if (!pos.equals(state.pos)) {
            state.pos = pos.immutable();
            state.last = block;
        }
        state.target = target;
        return target.hover(player, pos, block, blockHit);
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

    /**
     * Points the hand at its spot from where its shoulder is drawn this frame, after the torso has
     * leant. Called last, after the interaction runtime.
     */
    public static void aimArm(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !EMFCompatCore.isCompatEnabled()) return;
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
