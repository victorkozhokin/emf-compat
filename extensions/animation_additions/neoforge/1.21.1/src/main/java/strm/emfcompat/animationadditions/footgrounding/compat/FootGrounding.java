package strm.emfcompat.animationadditions.footgrounding.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.animationadditions.footgrounding.FootGroundingFeature;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.Smoothing;

import java.util.UUID;
import java.util.function.Function;

/**
 * Finds the floor under each foot and plants both feet on it.
 *
 * <p>Detection: right before the model is animated the pose stack is the model's space
 * ({@link IKFrame}). The bottom of each straight leg is carried into the world, and a few rays go
 * down from there through block collision shapes, so steps, slabs, snow layers and carpets all
 * count. The highest hit under a foot is its floor; its distance below the foot, in model pixels,
 * is the foot's drop.</p>
 *
 * <p>Body: the model is lowered by the drop of the foot that hangs most (capped at
 * {@link #MAX_LOWER}), so that foot reaches its floor. Both feet can hang at once, on stairs.</p>
 *
 * <p>Legs: each foot is planted on its own floor against the lowered body - a foot whose floor is
 * higher rises by the difference. The leg is pitched forward a little (at most {@link #MAX_BEND})
 * and the rest is taken by moving its hip up, the leg sliding into the torso: the legs are one
 * bone, and bending it the full way looked far worse.</p>
 *
 * <p>Whatever animation plays - walking, crouching, another addon's pose - the leg offset is added
 * on top of it after the pack has animated ({@link #apply}), so nothing is replaced.</p>
 */
public final class FootGrounding {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatFootGrounding");

    private static final Vector3f RIGHT_HIP = new Vector3f(-1.9f, 12f, 0f);
    private static final Vector3f LEFT_HIP = new Vector3f(1.9f, 12f, 0f);
    private static final float LEG = 12f;
    /** Probe offsets around the foot centre, model pixels: the sole is 4x4, so just inside its corners. */
    private static final float[][] PROBES = {{0, 0}, {1.8f, 1.8f}, {-1.8f, 1.8f}, {1.8f, -1.8f}, {-1.8f, -1.8f}};
    /**
     * Rays start this far above the ground level, blocks, so a step in front of the foot - higher
     * than where the foot is now - is seen too. A rise past {@link #MAX_STEP} is a wall.
     */
    private static final double RAY_UP = 0.7;
    private static final double RAY_DOWN = 0.8;

    /** Below this, model pixels, a foot counts as standing on its floor. */
    private static final float MIN_STEP = 0.75f;
    /** Past this the foot is over a drop, not a step, and is left hanging. */
    private static final float MAX_STEP = 10f;
    /** How far the body is lowered at most, model pixels. A slab is ~8.5. */
    private static final float MAX_LOWER = 9f;
    /** How far forward (pitch) a raised leg turns at most; the rest of the rise is the hip moving up. */
    private static final double MAX_BEND = Math.toRadians(12);

    /** A raised leg steps forward a little, and out from the body, model pixels... */
    private static final float STEP_FORWARD = 2f;
    private static final float STEP_OUT = 1.5f;
    /** ...in full once the hip has slid up this far. */
    private static final float STEP_FULL_AT = 3f;

    /** A leg swung forward past this, radians, looks for a step ahead of it. */
    private static final float SWING_FORWARD = 0.1f;

    private static final double LOWER_SECONDS = 0.12;
    private static final double RAISE_SECONDS = 0.05;
    private static final double SETTLE_SECONDS = 0.15;

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private FootGrounding() {
    }

    private static final class State {
        float lower, rightBend, leftBend;
        /** The legs as the animation left them last frame, {xRot, yRot, zRot}; null before one. */
        float[] rightPose, leftPose;
        String logged = "";
    }

    /**
     * Called with the pose stack right before the model is animated: solves (at most every
     * {@link EntityStates#SOLVE_EVERY_NANOS}) and lowers the model by the current amount.
     */
    public static void modelPose(AbstractClientPlayer player, PoseStack stack) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        EntityStates.Entry<State> entry = STATES.seen(uuid, now);
        State state = entry.value;
        double dt = EntityStates.due(entry, now);
        if (dt >= 0) solve(player, stack, state, dt);

        if (state.lower > 1e-3f) {
            // Model space: +y is down, one unit is 16 pixels.
            stack.translate(0f, state.lower / 16f, 0f);
        }
    }

    private static void solve(AbstractClientPlayer player, PoseStack stack, State state, double dt) {
        float targetLower = 0f, right = 0f, left = 0f;
        float plantRight = 0f, plantLeft = 0f;
        String decided;

        String why = ineligible(player);
        if (why != null) {
            decided = "off:" + why;
        } else {
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            IKFrame frame = IKFrame.capture(stack.last().pose(), camera);
            // The body goes by the floor under the hips: steady whatever the stride does. A foot
            // swung far back in the stride is in the air anyway and must not pull the body down.
            // Measured from where the model stands before this frame's lowering.
            // A floor above the straight leg is only the model shifted down by something else
            // (the crouch's render offset, an animation library moving the whole pose): never
            // lift the legs for it.
            float rawRight = drop(player, frame, RIGHT_HIP, null);
            float rawLeft = drop(player, frame, LEFT_HIP, null);
            right = Math.max(0f, rawRight);
            left = Math.max(0f, rawLeft);
            // Down onto the lowest floor under a foot; a foot over a drop-off does not count.
            float low = Math.max(right, left) <= MAX_STEP ? Math.max(right, left) : Math.min(right, left);
            if (low >= MIN_STEP && low <= MAX_STEP) targetLower = Math.min(low, MAX_LOWER);
            decided = targetLower > 0f ? "lowered" : "flat";

            plantRight = plant(player, frame, RIGHT_HIP, state.rightPose, targetLower, rawRight);
            plantLeft = plant(player, frame, LEFT_HIP, state.leftPose, targetLower, rawLeft);
        }

        // Body and legs both from their targets, not the legs from the smoothed body: the legs
        // neither lag the body nor keep turning after it has settled. A foot goes up onto a step
        // quickly, so it does not sink into it, and comes back down gently.
        float k = Smoothing.snapFirst(dt, LOWER_SECONDS);
        state.lower += (targetLower - state.lower) * k;
        state.rightBend += (plantRight - state.rightBend)
                * Smoothing.snapFirst(dt, plantRight > state.rightBend ? RAISE_SECONDS : SETTLE_SECONDS);
        state.leftBend += (plantLeft - state.leftBend)
                * Smoothing.snapFirst(dt, plantLeft > state.leftBend ? RAISE_SECONDS : SETTLE_SECONDS);
        // Per-frame trace while the feet do anything; debug only.
        if (FootGroundingFeature.isTrace() && why == null && (state.lower > 0.05f || state.rightBend > 0.05f || state.leftBend > 0.05f
                || plantRight > 0.05f || plantLeft > 0.05f)) {
            LOGGER.info("[FootTrace] x={} y={} R={} L={} low={} tl={} pr={} pl={} rb={} lb={} rp={} lp={}",
                    String.format("%.3f", player.getX()), String.format("%.3f", player.getY()),
                    String.format("%.2f", right), String.format("%.2f", left),
                    String.format("%.2f", state.lower), String.format("%.2f", targetLower),
                    String.format("%.2f", plantRight), String.format("%.2f", plantLeft),
                    String.format("%.2f", state.rightBend), String.format("%.2f", state.leftBend),
                    state.rightPose == null ? "-" : String.format("%.2f", state.rightPose[0]),
                    state.leftPose == null ? "-" : String.format("%.2f", state.leftPose[0]));
        }

        // One line per change of what was decided, not per frame.
        if (!decided.equals(state.logged)) {
            LOGGER.info("[FootGrounding] {} {} (R={} L={})", player.getName().getString(), decided,
                    String.format("%.2f", right), String.format("%.2f", left));
            state.logged = decided;
        }
    }

    /**
     * How far a foot rises against the body lowered by {@code lower}: onto its own floor, and a
     * foot the animation swings forward over a higher step goes up onto the step, in time with the
     * stride.
     */
    private static float plant(AbstractClientPlayer player, IKFrame frame, Vector3f hip, float[] pose,
                               float lower, float hipDrop) {
        float under = Math.max(0f, hipDrop);
        float plant = Math.max(0f, Math.min(MAX_STEP, lower - under));
        if (pose != null && pose[0] < -SWING_FORWARD) {
            // Measured from the floor under the hip, so a model shifted down cancels out.
            float ahead = drop(player, frame, hip, pose) - Math.min(0f, hipDrop);
            if (ahead < under - MIN_STEP) plant = Math.max(plant, Math.max(0f, Math.min(MAX_STEP, lower - ahead)));
        }
        return plant;
    }

    /** Why the feet are left alone this frame, or {@code null} when they are grounded. */
    private static String ineligible(AbstractClientPlayer player) {
        if (!FootGroundingFeature.isEnabled() || !EMFCompatCore.isCompatEnabled()) return "disabled";
        if (EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())) return "first-person";
        // Whatever animation plays, the offset goes on top of it; only a pose with no floor under
        // the feet is left alone.
        if (!player.onGround()) return "airborne";
        if (player.isPassenger() || player.isInWaterOrBubble() || player.isFallFlying()
                || player.isSleeping()) return "state";
        if (player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING) return "pose";
        return null;
    }

    /**
     * How far below the bottom of the straight leg its floor is, model pixels; the ray length if
     * there is none. The highest hit of the probes wins: a sole half over an edge still stands.
     */
    private static float drop(AbstractClientPlayer player, IKFrame frame, Vector3f hip, float[] pose) {
        // Where the foot is across the ground; its height is the straight leg's, the ground level.
        Vector3f sole = new Vector3f(0f, LEG, 0f);
        if (pose != null) new Quaternionf().rotationZYX(pose[2], pose[1], pose[0]).transform(sole);
        Vector3f base = new Vector3f(hip).add(sole.x, LEG, sole.z);
        Vec3 best = null;
        for (float[] probe : PROBES) {
            Vec3 foot = frame.jointWorld(new Vector3f(base).add(probe[0], 0f, probe[1]));
            BlockHitResult hit = player.level().clip(new ClipContext(
                    foot.add(0, RAY_UP, 0), foot.add(0, -RAY_DOWN, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != Direction.UP) continue;
            // A ray that starts inside a block (a wall ahead) hits right where it starts.
            if (hit.getLocation().y >= foot.y + RAY_UP - 1e-3) continue;
            if (best == null || hit.getLocation().y > best.y) best = hit.getLocation();
        }
        if (best == null) return (float) (RAY_DOWN * 16);
        Vec3 centre = frame.jointWorld(base);
        float drop = frame.relativeToJoint(new Vec3(centre.x, best.y, centre.z), hip).y - LEG;
        // Higher than a step: a wall, the foot stays where it is.
        return drop < -MAX_STEP ? 0f : drop;
    }

    /**
     * Keeps the legs as the pack animated them, before any offset of ours, so the next frame looks
     * under the feet where they really are. Called from the main model's animation only.
     */
    public static void recordAnimated(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null) return;
        state.rightPose = pose(parts.apply("right_leg"));
        state.leftPose = pose(parts.apply("left_leg"));
    }

    private static float[] pose(ModelPart leg) {
        return leg == null ? null : new float[]{leg.xRot, leg.yRot, leg.zRot};
    }

    /**
     * What to add to a leg on top of its animation: {pitch, lift}. Pitch forward in radians
     * (negative xRot), lift = how far the hip pivot moves up, pixels. {@code null} when the leg
     * is left as animated.
     */
    public static float[] legOffset(UUID uuid, boolean right) {
        State state = STATES.fresh(uuid);
        if (state == null) return null;
        float bend = right ? state.rightBend : state.leftBend;
        if (bend < 0.05f) return null;
        double theta = Math.min(MAX_BEND, Math.acos(Math.max(0f, (LEG - bend) / LEG)));
        // What the slight bend leaves, the hip takes: the leg slides up into the torso.
        float lift = Math.max(0f, bend - LEG * (1f - (float) Math.cos(theta)));
        return new float[]{-(float) theta, lift};
    }

    /** Adds the leg offsets on top of the animated legs. Called after the pack has animated. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        offset(parts.apply("right_leg"), legOffset(uuid, true), -1f);
        offset(parts.apply("left_leg"), legOffset(uuid, false), 1f);
    }

    /**
     * A raised leg also steps forward a little and out from the body, so the top
     * of the leg sliding up does not go into the torso. {@code side} is -1 right, +1 left (model x).
     */
    private static void offset(ModelPart leg, float[] offset, float side) {
        if (leg == null || offset == null) return;
        float step = Math.min(1f, offset[1] / STEP_FULL_AT);
        leg.xRot += offset[0];
        leg.y -= offset[1];
        leg.z -= STEP_FORWARD * step;
        leg.x += side * STEP_OUT * step;
    }
}
