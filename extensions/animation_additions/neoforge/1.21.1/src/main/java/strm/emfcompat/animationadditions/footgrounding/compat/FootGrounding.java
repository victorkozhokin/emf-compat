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
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.animationadditions.footgrounding.FootGroundingFeature;

import java.util.HashMap;
import java.util.Map;
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
    private static final double RAY_UP = 0.1;
    private static final double RAY_DOWN = 0.8;

    /** Below this, model pixels, a foot counts as standing on its floor. */
    private static final float MIN_STEP = 0.75f;
    /** Past this the foot is over a drop, not a step, and is left hanging. */
    private static final float MAX_STEP = 10f;
    /** How far the body is lowered at most, model pixels. A slab is ~8.5. */
    private static final float MAX_LOWER = 9f;
    /** How far forward (pitch) a raised leg turns at most; the rest of the rise is the hip moving up. */
    private static final double MAX_BEND = Math.toRadians(12);

    private static final double LOWER_SECONDS = 0.05;
    private static final double BEND_SECONDS = 0.02;
    private static final long SOLVE_EVERY_NANOS = 2_000_000L;
    private static final long STALE_NANOS = 200_000_000L;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private FootGrounding() {
    }

    private static final class State {
        float lower, rightBend, leftBend;
        long solvedAt, seenAt;
        String logged = "";
    }

    /**
     * Called with the pose stack right before the model is animated: solves (at most every
     * {@link #SOLVE_EVERY_NANOS}) and lowers the model by the current amount.
     */
    public static void modelPose(AbstractClientPlayer player, PoseStack stack) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        if (STATES.size() > 64) STATES.clear();
        State state = STATES.computeIfAbsent(uuid, k -> new State());
        state.seenAt = now;

        if (state.solvedAt == 0 || now - state.solvedAt >= SOLVE_EVERY_NANOS) {
            double dt = state.solvedAt == 0 ? 0 : Math.min(0.1, (now - state.solvedAt) / 1e9);
            state.solvedAt = now;
            solve(player, stack, state, dt);
        }

        if (state.lower > 1e-3f) {
            // Model space: +y is down, one unit is 16 pixels.
            stack.translate(0f, state.lower / 16f, 0f);
        }
    }

    private static void solve(AbstractClientPlayer player, PoseStack stack, State state, double dt) {
        float targetLower = 0f, right = 0f, left = 0f;
        boolean grounded = false;
        String decided;

        String why = ineligible(player);
        if (why != null) {
            decided = "off:" + why;
        } else {
            grounded = true;
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            IKFrame frame = IKFrame.capture(stack.last().pose(), camera);
            // Measured from where the model stands before this frame's lowering.
            right = drop(player, frame, RIGHT_HIP);
            left = drop(player, frame, LEFT_HIP);
            // Down onto the lowest floor under a foot; a foot over a drop-off does not count.
            float low = Math.max(right, left) <= MAX_STEP ? Math.max(right, left) : Math.min(right, left);
            if (low >= MIN_STEP && low <= MAX_STEP) targetLower = Math.min(low, MAX_LOWER);
            decided = targetLower > 0f ? "lowered" : "flat";
        }

        float kLower = dt == 0 ? 1f : (float) (1 - Math.exp(-dt / LOWER_SECONDS));
        state.lower += (targetLower - state.lower) * kLower;

        // Each foot onto its own floor, measured against the body as lowered now: a foot whose
        // floor is higher than where the lowered body puts it rises by the difference.
        float plantRight = grounded ? Math.max(0f, Math.min(MAX_STEP, state.lower - right)) : 0f;
        float plantLeft = grounded ? Math.max(0f, Math.min(MAX_STEP, state.lower - left)) : 0f;
        float kBend = dt == 0 ? 1f : (float) (1 - Math.exp(-dt / BEND_SECONDS));
        state.rightBend += (plantRight - state.rightBend) * kBend;
        state.leftBend += (plantLeft - state.leftBend) * kBend;

        // One line per change of what was decided, not per frame.
        if (!decided.equals(state.logged)) {
            LOGGER.info("[FootGrounding] {} {} (R={} L={})", player.getName().getString(), decided,
                    String.format("%.2f", right), String.format("%.2f", left));
            state.logged = decided;
        }
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
    private static float drop(AbstractClientPlayer player, IKFrame frame, Vector3f hip) {
        Vec3 best = null;
        for (float[] probe : PROBES) {
            Vec3 foot = frame.jointWorld(new Vector3f(hip).add(probe[0], LEG, probe[1]));
            BlockHitResult hit = player.level().clip(new ClipContext(
                    foot.add(0, RAY_UP, 0), foot.add(0, -RAY_DOWN, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != Direction.UP) continue;
            if (best == null || hit.getLocation().y > best.y) best = hit.getLocation();
        }
        if (best == null) return (float) (RAY_DOWN * 16);
        Vec3 centre = frame.jointWorld(new Vector3f(hip).add(0, LEG, 0));
        return frame.relativeToJoint(new Vec3(centre.x, best.y, centre.z), hip).y - LEG;
    }

    /**
     * What to add to a leg on top of its animation: {pitch, lift}. Pitch forward in radians
     * (negative xRot), lift = how far the hip pivot moves up, pixels. {@code null} when the leg
     * is left as animated.
     */
    public static float[] legOffset(UUID uuid, boolean right) {
        State state = STATES.get(uuid);
        if (state == null || System.nanoTime() - state.seenAt > STALE_NANOS) return null;
        float bend = right ? state.rightBend : state.leftBend;
        if (bend < 0.05f) return null;
        double theta = Math.min(MAX_BEND, Math.acos(Math.max(0f, (LEG - bend) / LEG)));
        // What the slight bend leaves, the hip takes: the leg slides up into the torso.
        float lift = Math.max(0f, bend - LEG * (1f - (float) Math.cos(theta)));
        return new float[]{-(float) theta, lift};
    }

    /** Adds the leg offsets on top of the animated legs. Called after the pack has animated. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        offset(parts.apply("right_leg"), legOffset(uuid, true));
        offset(parts.apply("left_leg"), legOffset(uuid, false));
    }

    private static void offset(ModelPart leg, float[] offset) {
        if (leg == null || offset == null) return;
        leg.xRot += offset[0];
        leg.y -= offset[1];
    }
}
