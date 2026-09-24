package strm.emfcompat.animationadditions.wallhand;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKMath;
import strm.emfcompat.core.ik.IKResult;
import strm.emfcompat.core.ik.OneBoneIK;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Hand on the wall: standing (or walking slowly) facing a wall, both palms rest on it; with a wall
 * beside a shoulder instead, the arm on that side rests its palm on it, ahead of and below the
 * shoulder.
 *
 * <p>Rays go ahead and sideways from each shoulder; a wall face square to the ray and in reach of
 * the arm is aimed at with {@link OneBoneIK} - a wall ahead first, else the nearer one beside. The arm
 * fades in over the pack's animation and back out when the wall is gone, the player swings or
 * uses an item, or another addon poses the arms.</p>
 */
public final class WallHand {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatWallHand");

    public static final String KEY_ENABLED = "wallhand.enabled";

    /** Model space: pixels, y down, the model facing -z. Vanilla shoulder pivots. */
    private static final Vector3f RIGHT_SHOULDER = new Vector3f(-5f, 2f, 0f);
    private static final Vector3f LEFT_SHOULDER = new Vector3f(5f, 2f, 0f);
    /** From the shoulder pivot to the palm, in pixels. */
    private static final float ARM = 10f;
    /** How far sideways and ahead a wall is looked for, blocks; the arm's reach decides the rest. */
    private static final double LOOK_SIDEWAYS = 1.0;
    private static final double LOOK_AHEAD = 1.0;
    /**
     * Where on the wall the palm goes. The arm is one straight bone, so the palm has to be an arm's
     * length from the shoulder: this far below it, blocks, and as far ahead along the wall as that
     * leaves, times {@link #PALM_REACH} so the hand presses rather than falls short.
     */
    private static final double PALM_BELOW = 0.35;
    private static final double PALM_REACH = 0.95;
    private static final double PALM_OFF = 0.02;
    /** Nearer than this along the wall, blocks, and the arm would have to bend back into the body. */
    private static final double MIN_AHEAD = 0.1;
    /** Past the arm's length, as a share of it: the wall is out of reach. */
    private static final float MAX_REACH = 1.0f;
    /** Faster than this, blocks per tick, the player walks past walls rather than leaning on one. */
    private static final double SLOW_BELOW = 0.08;
    private static final double FADE_IN_SECONDS = 0.18;
    private static final double FADE_OUT_SECONDS = 0.1;
    private static final long SOLVE_EVERY_NANOS = 2_000_000L;
    private static final long STALE_NANOS = 200_000_000L;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private WallHand() {
    }

    private static final class State {
        /** Per arm, right then left: the aimed {x, y} and how much of it shows. */
        final float[][] aim = new float[2][2];
        final float[] weight = new float[2];
        long solvedAt, seenAt;
        String logged = "";
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Hand on the wall", true,
                "On", "Standing at a wall, rest the hands on it: both facing it, the near one beside it.",
                "Off", "Leave the arms to EMF.");
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    /** Called with the model's space right before it is animated. */
    public static void modelPose(AbstractClientPlayer player, IKFrame frame) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        if (STATES.size() > 64) STATES.clear();
        State state = STATES.computeIfAbsent(uuid, k -> new State());
        state.seenAt = now;
        if (state.solvedAt != 0 && now - state.solvedAt < SOLVE_EVERY_NANOS) return;
        double dt = state.solvedAt == 0 ? 0 : Math.min(0.1, (now - state.solvedAt) / 1e9);
        state.solvedAt = now;

        float[][] aims = new float[2][];
        String decided = ineligible(player);
        if (decided == null) {
            // Facing a wall: both hands on it. Otherwise the nearer wall beside a shoulder.
            IKResult frontRight = ahead(player, frame, RIGHT_SHOULDER);
            IKResult frontLeft = ahead(player, frame, LEFT_SHOULDER);
            if (frontRight != null || frontLeft != null) {
                aims[0] = angles(frontRight);
                aims[1] = angles(frontLeft);
                decided = frontRight != null && frontLeft != null ? "front" : "front-corner";
            } else {
                IKResult right = beside(player, frame, RIGHT_SHOULDER, -1f);
                IKResult left = beside(player, frame, LEFT_SHOULDER, 1f);
                if (right != null && (left == null || right.reach() <= left.reach())) {
                    aims[0] = angles(right);
                    decided = "right";
                } else if (left != null) {
                    aims[1] = angles(left);
                    decided = "left";
                } else {
                    decided = "none";
                }
            }
        }

        for (int arm = 0; arm < 2; arm++) {
            boolean on = aims[arm] != null;
            if (on) state.aim[arm] = aims[arm];
            double tau = on ? FADE_IN_SECONDS : FADE_OUT_SECONDS;
            float k = dt == 0 ? (on ? 0f : 1f) : (float) (1 - Math.exp(-dt / tau));
            state.weight[arm] += ((on ? 1f : 0f) - state.weight[arm]) * k;
        }

        if (!decided.equals(state.logged)) {
            LOGGER.info("[WallHand] {} {}", player.getName().getString(), decided);
            state.logged = decided;
        }
    }

    private static String ineligible(AbstractClientPlayer player) {
        if (!isEnabled() || !EMFCompatCore.isCompatEnabled()) return "off:disabled";
        if (EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())) return "off:first-person";
        if (!player.onGround() || player.isPassenger() || player.isSleeping()
                || player.isInWaterOrBubble()) return "off:state";
        if (player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING) return "off:pose";
        if (player.swinging || player.isUsingItem()) return "off:busy";
        if (Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) > SLOW_BELOW) return "off:moving";
        // Another addon posing the arms owns them.
        if (PoseManager.hasArmPoseExcept(player.getUUID(), "")) return "off:arm-pose";
        return null;
    }

    private static float[] angles(IKResult result) {
        return result == null ? null : new float[]{result.x(), result.y()};
    }

    /**
     * The arm aimed at the wall beside this shoulder, or {@code null} when there is none in reach.
     * {@code out} is the model x of this side.
     */
    private static IKResult beside(AbstractClientPlayer player, IKFrame frame, Vector3f shoulder, float out) {
        Vec3 side = horizontal(frame, new Vector3f(out, 0, 0));
        Vec3 forward = horizontal(frame, new Vector3f(0, 0, -1));
        if (side == null || forward == null) return null;
        Vec3 from = frame.jointWorld(shoulder);
        Hit wall = wall(player, from, side, LOOK_SIDEWAYS);
        if (wall == null) return null;

        // An arm's length from the shoulder: the wall's distance is given, the drop is as much as
        // leaves some reach along the wall (less the further the wall is - the arm comes up to
        // it), and the rest of the length goes ahead along the wall.
        double arm = armLength(frame, shoulder);
        double across = wall.distance;
        double spare = arm * arm - across * across - MIN_AHEAD * MIN_AHEAD;
        if (spare < 0) return null;
        double below = Math.min(PALM_BELOW, Math.sqrt(spare));
        double along = Math.sqrt(arm * arm - across * across - below * below);
        Vec3 palm = from.add(side.scale(across)).add(wall.normal.scale(PALM_OFF))
                .add(forward.scale(along)).add(0, -below, 0);
        return aim(frame, shoulder, palm);
    }

    /**
     * The arm aimed at the wall straight ahead of this shoulder, or {@code null} when there is none
     * in reach: the palm goes on the wall in front of the shoulder, as far down as makes an arm's
     * length.
     */
    private static IKResult ahead(AbstractClientPlayer player, IKFrame frame, Vector3f shoulder) {
        Vec3 forward = horizontal(frame, new Vector3f(0, 0, -1));
        if (forward == null) return null;
        Vec3 from = frame.jointWorld(shoulder);
        Hit wall = wall(player, from, forward, LOOK_AHEAD);
        if (wall == null) return null;
        double arm = armLength(frame, shoulder);
        if (wall.distance > arm) return null;
        double below = Math.sqrt(arm * arm - wall.distance * wall.distance);
        Vec3 palm = from.add(forward.scale(wall.distance)).add(wall.normal.scale(PALM_OFF)).add(0, -below, 0);
        return aim(frame, shoulder, palm);
    }

    private record Hit(double distance, Vec3 normal) {
    }

    /** A wall face square to {@code direction} within {@code range} of {@code from}. */
    private static Hit wall(AbstractClientPlayer player, Vec3 from, Vec3 direction, double range) {
        BlockHitResult hit = player.level().clip(new ClipContext(from, from.add(direction.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS || hit.getDirection().getAxis().isVertical()) return null;
        Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        // Square to the shoulder, not a corner glanced at an angle.
        if (normal.dot(direction) > -0.7) return null;
        return new Hit(hit.getLocation().subtract(from).dot(direction), normal);
    }

    /** A model direction carried into the world and laid flat, or {@code null} if it is vertical. */
    private static Vec3 horizontal(IKFrame frame, Vector3f modelDirection) {
        Vector3f world = frame.modelToWorld().transformDirection(modelDirection);
        Vec3 flat = new Vec3(world.x, 0, world.z);
        return flat.lengthSqr() < 1e-6 ? null : flat.normalize();
    }

    /** The arm's length in blocks, a little short so the hand presses rather than falls short. */
    private static double armLength(IKFrame frame, Vector3f shoulder) {
        return frame.jointWorld(shoulder).distanceTo(frame.jointWorld(new Vector3f(shoulder).add(0, ARM, 0)))
                * PALM_REACH;
    }

    private static IKResult aim(IKFrame frame, Vector3f shoulder, Vec3 palm) {
        IKResult result = OneBoneIK.solveXY(frame, shoulder, palm, ARM, 0f, 0f);
        return result == null || result.reach() > MAX_REACH ? null : result;
    }

    /** How much of an arm the wall has, 0 to 1; {@code right} picks the arm. */
    public static float weight(UUID uuid, boolean right) {
        State state = STATES.get(uuid);
        if (state == null || System.nanoTime() - state.seenAt > STALE_NANOS) return 0f;
        return state.weight[right ? 0 : 1];
    }

    /** Blends each arm towards its aim on the wall, over whatever it was animated to. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.get(uuid);
        if (state == null || System.nanoTime() - state.seenAt > STALE_NANOS) return;
        blend(parts.apply("right_arm"), state.aim[0], state.weight[0]);
        blend(parts.apply("left_arm"), state.aim[1], state.weight[1]);
    }

    private static void blend(ModelPart arm, float[] aim, float weight) {
        if (arm == null || weight < 1e-3f) return;
        arm.xRot += IKMath.wrap(aim[0] - arm.xRot) * weight;
        arm.yRot += IKMath.wrap(aim[1] - arm.yRot) * weight;
        arm.zRot += IKMath.wrap(0f - arm.zRot) * weight;
    }
}
