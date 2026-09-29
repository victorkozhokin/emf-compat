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
 * front) or taps. Only the arm moves: the torso stays as the pack draws it.
 *
 * <p>By the look, as a click is: the spot looked at is the one used. The hand only goes where the
 * click would do something, and only within reach of the arm - no using a block from across the
 * room. The use itself is instant; the gesture plays out after it,
 * as a lever's hand follows the handle over.</p>
 */
public final class BlockUse implements InteractionProvider {

    public static final BlockUse INSTANCE = new BlockUse();
    public static final String KEY_ENABLED = "blockuse.enabled";

    private static final List<BlockTarget> TARGETS = List.of(new ChiseledShelf(), new Jukebox(), new Campfire(), new Vault());

    /** Below a button press, above doors and chests. */
    private static final int PRIORITY = 8;
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.12, 0.18, 0.05);

    /** Model space: pixels, y down, facing -z. */
    private static final Vector3f RIGHT_SHOULDER = new Vector3f(-5f, 2f, 0f);
    private static final Vector3f LEFT_SHOULDER = new Vector3f(5f, 2f, 0f);
    private static final float ARM = 11f;
    /** Looked for this far along the look, blocks. */
    private static final double RANGE = 3.0;
    /** Past the arm's length, as a share of it: further and the hand does not go. */
    private static final float MAX_REACH = 2.5f;
    /** Faster than this, blocks per tick, the player walks past. */
    private static final double SLOW_BELOW = 0.15;

    /** Pixels out of the block: where the hand waits; how deep a put goes in; from where and to where a take pulls. */
    private static final float HOVER_OUT = 1.5f;
    private static final float PUT_IN = 3f;
    private static final float TAKE_FROM = -2f;
    private static final float TAKE_TO = 5f;
    private static final float TAP_IN = 1.5f;
    private static final double GESTURE_SECONDS = 0.35;
    /**
     * A turn: the item goes in over the first {@link #TURN_IN} of it and stays in, then turns about
     * its own length by {@link #TURN_ANGLE} - clockwise as the player sees it - and holds.
     */
    private static final double TURN_SECONDS = 0.7;
    private static final double TURN_IN = 0.3;
    private static final float TURN_ANGLE = (float) Math.toRadians(40);
    private static final double GRIP_SECONDS = 0.05;

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private BlockUse() {
    }

    private static final class State {
        /** The block looked at, as it was last seen, and its target. */
        BlockPos pos;
        Object last;
        BlockTarget target;
        BlockTarget.Gesture gesture;
        long gestureAt;
        boolean right = true, shown;
        /** The hand's (or the item's) point in model pixels. */
        final Vector3f grip = new Vector3f();
        /** The held item's {gripY, gripZ, tipY, tipZ} when it goes on the point, and its turn about its length, radians. */
        float[] item;
        float twist;
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
                Object seen = state.target.matches(block) ? state.target.snapshot(player.level(), state.pos, block) : block;
                if (!seen.equals(state.last)) {
                    BlockTarget.Gesture gesture = state.target.matches(block) ? state.target.changed(state.pos, state.last, seen) : null;
                    if (gesture != null) {
                        state.gesture = gesture;
                        state.gestureAt = now;
                    }
                    state.last = seen;
                }
            }
            double seconds = state.gesture != null && state.gesture.motion() == BlockTarget.Motion.TURN ? TURN_SECONDS : GESTURE_SECONDS;
            double t = state.gesture == null ? 1 : (now - state.gestureAt) / 1e9 / seconds;
            if (t >= 1) state.gesture = null;
            float twist = 0f;

            BlockTarget.Spot spot;
            float outwards;
            if (state.gesture != null) {
                spot = state.gesture.spot();
                float s = (float) Math.sin(Math.PI * t);
                switch (state.gesture.motion()) {
                    case PUT -> outwards = HOVER_OUT - PUT_IN * s;
                    case TAKE -> {
                        float e = (float) (1 - (1 - t) * (1 - t));
                        outwards = TAKE_FROM + (TAKE_TO - TAKE_FROM) * e;
                    }
                    case TURN -> {
                        outwards = HOVER_OUT - PUT_IN * (float) Math.min(1, t / TURN_IN);
                        float k = (float) Mth.clamp((t - TURN_IN) / (1 - TURN_IN) * 1.6, 0, 1);
                        twist = TURN_ANGLE * k * k * (3 - 2 * k);
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
            float[] item = state.target == null ? null : state.target.item();
            float[] aim;
            if (item != null) {
                // The item's tip on the point, as a tool's in Mining.
                float[] solved = solveItem(new Vector3f(model).sub(shoulder), item);
                if (solved[2] > MAX_REACH) {
                    context.decide("out-of-reach");
                    return;
                }
                aim = new float[]{solved[0], solved[1]};
            } else {
                IKResult ik = OneBoneIK.solveXY(frame, shoulder, point, ARM, 0f, 0f);
                if (ik == null || ik.reach() > MAX_REACH) {
                    context.decide("out-of-reach");
                    return;
                }
                aim = new float[]{ik.x(), ik.y()};
            }
            state.item = item;
            state.twist = twist;
            Effector effector = right ? Effector.RIGHT_ARM : Effector.LEFT_ARM;
            if (!state.shown || right != state.right || InteractionRuntime.weight(player.getUUID(), effector, id()) < 1e-3f) {
                state.grip.set(model);
            } else {
                state.grip.lerp(model, Smoothing.follow(context.dt(), GRIP_SECONDS));
            }
            state.right = right;
            shown = true;
            out.add(Candidate.single(id(), Category.USE, PRIORITY, 1f, TIMING, effector, aim));
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
            state.last = target.snapshot(player.level(), pos, block);
        }
        state.target = target;
        return target.hover(player, pos, block, blockHit);
    }

    /**
     * Points the hand at its spot from where its shoulder is drawn this frame. Called last, after
     * the interaction runtime.
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
        if (state.item != null) {
            float[] solved = solveItem(to, state.item);
            Quaternionf q = new Quaternionf().rotationZYX(0f, solved[1], solved[0]);
            if (state.twist != 0f) {
                // Turned about the item's own length, as a key is: clockwise seen from behind it
                // is a right-handed turn about the way it points.
                Vector3f along = q.transform(new Vector3f(0f, state.item[2] - state.item[0], state.item[3] - state.item[1]).normalize());
                q = new Quaternionf().rotateAxis(state.twist, along).mul(q);
            }
            Vector3f euler = zyx(q);
            arm.xRot += IKMath.wrap(euler.x - arm.xRot) * w;
            arm.yRot += IKMath.wrap(euler.y - arm.yRot) * w;
            arm.zRot += IKMath.wrap(euler.z - arm.zRot) * w;
            return;
        }
        to.normalize();
        // As OneBoneIK: the arm hangs along +y.
        float x = -(float) Math.acos(Mth.clamp(to.y, -1f, 1f));
        float y = (float) Math.atan2(-to.x, -to.z);
        arm.xRot += IKMath.wrap(x - arm.xRot) * w;
        arm.yRot += IKMath.wrap(y - arm.yRot) * w;
    }

    /**
     * The arm's {xRot, yRot} putting the held item's tip on a point {@code to} pixels from the
     * shoulder, and how far the point is as a share of the tip's reach - as {@code Mining.solve}.
     * The arm's turn about x turns the tip round by the same angle from where it hangs.
     */
    private static float[] solveItem(Vector3f to, float[] item) {
        float distance = to.length();
        if (distance < 1e-3f) return new float[]{0f, 0f, 0f};
        float offset = (float) Math.atan2(item[3], item[2]);
        float x = -(float) Math.acos(Mth.clamp(to.y / distance, -1f, 1f)) - offset;
        float yaw = (float) Math.atan2(-to.x, -to.z);
        return new float[]{x, yaw, distance / (float) Math.hypot(item[2], item[3])};
    }

    /**
     * {xRot, yRot, zRot} of a part turned by {@code q}, for the part's R = Rz Ry Rx - as
     * {@code Mining.zyx}: joml's getEulerAnglesZYX gave a wrong pose.
     */
    private static Vector3f zyx(Quaternionf q) {
        Vector3f c0 = q.transform(new Vector3f(1f, 0f, 0f));
        Vector3f c1 = q.transform(new Vector3f(0f, 1f, 0f));
        Vector3f c2 = q.transform(new Vector3f(0f, 0f, 1f));
        float y = (float) Math.asin(Mth.clamp(-c0.z, -1f, 1f));
        float x = (float) Math.atan2(c1.z, c2.z);
        float z = (float) Math.atan2(c0.y, c0.x);
        return new Vector3f(x, y, z);
    }
}
