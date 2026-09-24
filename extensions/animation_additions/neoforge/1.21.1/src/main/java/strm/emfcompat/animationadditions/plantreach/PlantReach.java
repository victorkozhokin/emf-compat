package strm.emfcompat.animationadditions.plantreach;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.animationadditions.wallhand.WallHand;
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
 * Hands brushing plants: standing in or walking through grass, ferns, flowers, crops and the like,
 * each hand reaches for the top of a plant on its side, the way a hand trails over a wheat field.
 *
 * <p>Plants are bushes ({@link BushBlock}: grass, tall grass, ferns, flowers, crops, saplings, berry
 * bushes, mushrooms). Each arm is aimed with {@link OneBoneIK} at the point on the top of the
 * plant nearest to where its hand hangs - or as high as the arm reaches, for a plant taller than
 * that - on its side and not behind, so the hand follows the plants as they come closer and fall
 * behind. Of a plant the player stands in, only the part sticking out from under the body on the
 * hand's side counts: in a dense field or on the edge between two blocks the hands reached for the
 * feet.</p>
 *
 * <p>The aim is smoothed, since the plant changes as the player walks; an arm on a wall
 * ({@link WallHand}) is left to it.</p>
 */
public final class PlantReach {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatPlantReach");

    public static final String KEY_ENABLED = "plantreach.enabled";

    /** Model space: pixels, y down, the model facing -z. Vanilla shoulder pivots. */
    private static final Vector3f RIGHT_SHOULDER = new Vector3f(-5f, 2f, 0f);
    private static final Vector3f LEFT_SHOULDER = new Vector3f(5f, 2f, 0f);
    /** From the shoulder pivot to the palm, in pixels. */
    private static final float ARM = 10f;
    /** The hand goes this far into the plant's top, blocks, so it touches rather than hovers. */
    private static final double INTO_TOP = 0.05;
    /** The hand stays at least this far below the shoulder, blocks: no reaching up. */
    private static final double BELOW_SHOULDER = 0.25;
    /** How far clear of the hitbox, blocks, a reached point has to be. */
    private static final double BODY_MARGIN = 0.05;
    /** A plant this far behind the shoulder, blocks, is out of the hand's way. */
    private static final double BEHIND = 0.1;
    /** A plant on the wrong side of the body by more than this, blocks, is the other hand's. */
    private static final double ACROSS = 0.05;
    private static final double FADE_IN_SECONDS = 0.15;
    private static final double FADE_OUT_SECONDS = 0.2;
    /** How quickly the aim follows as the reached plant changes. */
    private static final double AIM_SECONDS = 0.08;
    /** Above this much of an arm on a wall, the plants leave it alone. */
    private static final float WALL_TAKES_ARM = 0.05f;
    private static final long SOLVE_EVERY_NANOS = 2_000_000L;
    private static final long STALE_NANOS = 200_000_000L;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private PlantReach() {
    }

    private static final class State {
        /** Per arm, right then left: the aimed {x, y} and how much of it shows. */
        final float[][] aim = new float[2][];
        final float[] weight = new float[2];
        long solvedAt, seenAt;
        String logged = "";
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Hands brush plants", true,
                "On", "In grass, crops or flowers, the hands reach for the plants beside you.",
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
            if (WallHand.weight(uuid, true) < WALL_TAKES_ARM) aims[0] = reach(player, frame, RIGHT_SHOULDER, -1f);
            if (WallHand.weight(uuid, false) < WALL_TAKES_ARM) aims[1] = reach(player, frame, LEFT_SHOULDER, 1f);
            decided = (aims[0] != null ? "R" : "-") + (aims[1] != null ? "L" : "-");
        }

        float kAim = dt == 0 ? 1f : (float) (1 - Math.exp(-dt / AIM_SECONDS));
        for (int arm = 0; arm < 2; arm++) {
            boolean on = aims[arm] != null;
            if (on) {
                if (state.aim[arm] == null || state.weight[arm] < 1e-3f) {
                    state.aim[arm] = aims[arm];
                } else {
                    state.aim[arm][0] += IKMath.wrap(aims[arm][0] - state.aim[arm][0]) * kAim;
                    state.aim[arm][1] += IKMath.wrap(aims[arm][1] - state.aim[arm][1]) * kAim;
                }
            }
            double tau = on ? FADE_IN_SECONDS : FADE_OUT_SECONDS;
            float k = dt == 0 ? (on ? 0f : 1f) : (float) (1 - Math.exp(-dt / tau));
            state.weight[arm] += ((on ? 1f : 0f) - state.weight[arm]) * k;
        }

        if (!decided.equals(state.logged)) {
            LOGGER.info("[PlantReach] {} {}", player.getName().getString(), decided);
            state.logged = decided;
        }
    }

    private static String ineligible(AbstractClientPlayer player) {
        if (!isEnabled() || !EMFCompatCore.isCompatEnabled()) return "off:disabled";
        if (EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())) return "off:first-person";
        if (player.isPassenger() || player.isSleeping() || player.isInWaterOrBubble()) return "off:state";
        if (player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING) return "off:pose";
        if (player.swinging || player.isUsingItem()) return "off:busy";
        // Another addon posing the arms owns them.
        if (PoseManager.hasArmPoseExcept(player.getUUID(), "")) return "off:arm-pose";
        return null;
    }

    /**
     * The arm aimed at a plant on this side, or {@code null} when there is none in reach.
     * {@code out} is the model x of this side.
     */
    private static float[] reach(AbstractClientPlayer player, IKFrame frame, Vector3f shoulder, float out) {
        Vector3f sidewaysWorld = frame.modelToWorld().transformDirection(new Vector3f(out, 0, 0));
        Vector3f aheadWorld = frame.modelToWorld().transformDirection(new Vector3f(0, 0, -1));
        Vec3 side = new Vec3(sidewaysWorld.x, 0, sidewaysWorld.z);
        Vec3 ahead = new Vec3(aheadWorld.x, 0, aheadWorld.z);
        if (side.lengthSqr() < 1e-6 || ahead.lengthSqr() < 1e-6) return null;
        side = side.normalize();
        ahead = ahead.normalize();

        Vec3 from = frame.jointWorld(shoulder);
        Vec3 hanging = frame.jointWorld(new Vector3f(shoulder).add(0, ARM, 0));
        Vec3 centre = frame.jointWorld(new Vector3f(0, 2, 0));
        double arm = from.distanceTo(hanging);
        double lowest = from.y - arm;
        double highest = from.y - BELOW_SHOULDER;

        Level level = player.level();
        Vec3 best = nearest(player, level, from, hanging, centre, side, ahead, lowest, highest);
        if (best == null || best.distanceTo(from) > arm) return null;
        IKResult result = OneBoneIK.solveXY(frame, shoulder, best, ARM, 0f, 0f);
        return result == null ? null : new float[]{result.x(), result.y()};
    }

    /**
     * The point on a plant's top nearest to where the hand hangs, on its side, ahead or beside but
     * not behind, and not under the body.
     */
    private static Vec3 nearest(AbstractClientPlayer player, Level level, Vec3 from, Vec3 hanging, Vec3 centre,
                                Vec3 side, Vec3 ahead, double lowest, double highest) {
        AABB footprint = player.getBoundingBox().inflate(BODY_MARGIN, 0, BODY_MARGIN);
        BlockPos feet = BlockPos.containing(hanging);
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BushBlock)) continue;
            VoxelShape shape = state.getShape(level, pos);
            if (shape.isEmpty()) continue;
            AABB box = shape.bounds().move(pos);
            // The point of the plant's top nearest the hand, no higher than the arm can bring it.
            double y = Math.min(highest, box.maxY - INTO_TOP);
            if (y < lowest || y < box.minY) continue;
            Vec3 point = new Vec3(Math.max(box.minX, Math.min(box.maxX, hanging.x)), y,
                    Math.max(box.minZ, Math.min(box.maxZ, hanging.z)));
            // Not under the body: a plant the player stands in is only reached where it sticks out
            // on the hand's side - otherwise, in a field or on the edge between two blocks, the hand
            // went for the feet.
            point = outside(point, box, footprint, side);
            if (point == null) continue;
            if (point.subtract(centre).dot(side) < ACROSS) continue;
            if (point.subtract(from).dot(ahead) < -BEHIND) continue;
            double distance = point.distanceTo(hanging);
            if (distance < bestDistance) {
                best = point;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * {@code point}, moved out from under the body towards the hand's side until it clears
     * {@code footprint}; {@code null} if it leaves the plant first.
     */
    private static Vec3 outside(Vec3 point, AABB plant, AABB footprint, Vec3 side) {
        for (int i = 0; i < 40; i++) {
            boolean under = point.x > footprint.minX && point.x < footprint.maxX
                    && point.z > footprint.minZ && point.z < footprint.maxZ;
            if (!under) return point;
            point = point.add(side.scale(0.025));
            if (point.x < plant.minX || point.x > plant.maxX || point.z < plant.minZ || point.z > plant.maxZ) {
                return null;
            }
        }
        return null;
    }

    /** Blends each arm towards its plant, over whatever it was animated to. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.get(uuid);
        if (state == null || System.nanoTime() - state.seenAt > STALE_NANOS) return;
        blend(parts.apply("right_arm"), state.aim[0], state.weight[0]);
        blend(parts.apply("left_arm"), state.aim[1], state.weight[1]);
    }

    private static void blend(ModelPart arm, float[] aim, float weight) {
        if (arm == null || aim == null || weight < 1e-3f) return;
        arm.xRot += IKMath.wrap(aim[0] - arm.xRot) * weight;
        arm.yRot += IKMath.wrap(aim[1] - arm.yRot) * weight;
        arm.zRot += IKMath.wrap(0f - arm.zRot) * weight;
    }
}
