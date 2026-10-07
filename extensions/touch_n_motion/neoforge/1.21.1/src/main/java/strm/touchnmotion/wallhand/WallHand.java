package strm.touchnmotion.wallhand;

import static strm.touchnmotion.interaction.Skeleton.LEFT_SHOULDER;
import static strm.touchnmotion.interaction.Skeleton.RIGHT_SHOULDER;
import strm.touchnmotion.interaction.Skeleton;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.touchnmotion.interaction.Candidate;
import strm.touchnmotion.interaction.Category;
import strm.touchnmotion.interaction.Effector;
import strm.touchnmotion.interaction.InteractionContext;
import strm.touchnmotion.interaction.InteractionProvider;
import strm.touchnmotion.interaction.SubLevels;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKResult;
import strm.emfcompat.core.ik.OneBoneIK;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Hand on the wall: standing (or walking slowly) facing a wall, both palms rest on it; with a wall
 * beside a shoulder instead, the arm on that side rests its palm on it, ahead of and below the
 * shoulder.
 *
 * <p>Rays go ahead and sideways from each shoulder; a wall face square to the ray and in reach of
 * the arm is aimed at with {@link OneBoneIK} - a wall ahead first, else the nearer one beside.</p>
 *
 * <p>A passive-contact provider: it only offers the aims. Both hands on a wall ahead are one
 * group - they go on together or not at all. The runtime fades them in and out and gives the arms
 * up to anything stronger (a swing, an item in use, another addon's pose).</p>
 */
public final class WallHand implements InteractionProvider {

    public static final WallHand INSTANCE = new WallHand();
    /** Above the plants in the passive band: an arm on a wall stays there. */
    private static final int PRIORITY = 20;
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.18, 0.1, 0);

    public static final String KEY_ENABLED = "wallhand.enabled";

    /** From the shoulder pivot to the palm, in pixels. */
    private static final float ARM = Skeleton.ARM_TO_PALM;
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
    private WallHand() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Hand on the wall", true,
                "On", "Standing at a wall, rest the hands on it: both facing it, the near one beside it.",
                "Off", "Leave the arms to EMF.");
    }

    @Override
    public String id() {
        return "WallHand";
    }

    @Override
    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        IKFrame frame = context.frame();
        if (WallSqueeze.isActive(player.getUUID())) {
            context.decide("squeeze-contact");
            return;
        }
        String why = ineligible(player);
        if (why != null) {
            context.decide(why);
            return;
        }
        // Facing a wall: both hands on it. Otherwise the nearer wall beside a shoulder.
        boolean rightFree = free(player, true), leftFree = free(player, false);
        Contact frontRight = rightFree ? ahead(player, frame, RIGHT_SHOULDER) : null;
        Contact frontLeft = leftFree ? ahead(player, frame, LEFT_SHOULDER) : null;
        if (frontRight != null || frontLeft != null) {
            Map<Effector, float[]> aims = new EnumMap<>(Effector.class);
            if (frontRight != null) { aims.put(Effector.RIGHT_ARM, angles(frontRight)); remember(context, Effector.RIGHT_ARM, frontRight); }
            if (frontLeft != null) { aims.put(Effector.LEFT_ARM, angles(frontLeft)); remember(context, Effector.LEFT_ARM, frontLeft); }
            out.add(Candidate.of(id(), Category.PASSIVE, PRIORITY, 1f, TIMING, aims).withTarget(java.util.List.of(frontRight == null ? "none" : frontRight.target, frontLeft == null ? "none" : frontLeft.target)));
            context.decide(aims.size() == 2 ? "front" : "front-corner");
            return;
        }
        Contact right = rightFree ? beside(player, frame, RIGHT_SHOULDER, -1f) : null;
        Contact left = leftFree ? beside(player, frame, LEFT_SHOULDER, 1f) : null;
        if (right != null && (left == null || right.aim.reach() <= left.aim.reach())) {
            remember(context, Effector.RIGHT_ARM, right);
            out.add(Candidate.single(id(), Category.PASSIVE, PRIORITY, 1f, TIMING, Effector.RIGHT_ARM, angles(right)).withTarget(right.target));
            context.decide("right");
        } else if (left != null) {
            remember(context, Effector.LEFT_ARM, left);
            out.add(Candidate.single(id(), Category.PASSIVE, PRIORITY, 1f, TIMING, Effector.LEFT_ARM, angles(left)).withTarget(left.target));
            context.decide("left");
        } else {
            context.decide("none");
        }
    }

    private static boolean free(AbstractClientPlayer player, boolean right) {
        boolean main = right == (player.getMainArm() == HumanoidArm.RIGHT);
        return player.getItemInHand(main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND).isEmpty();
    }

    private static String ineligible(AbstractClientPlayer player) {
        if (!player.onGround() || player.isPassenger() || player.isSleeping()
                || player.isInWaterOrBubble()) return "off:state";
        if (player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING) return "off:pose";
        if (Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) > SLOW_BELOW) return "off:moving";
        return null;
    }

    private record Contact(IKResult aim, Vec3 point, Vec3 normal, SubLevels.Space space, Object target) {}
    private static float[] angles(Contact result) {
        return new float[]{result.aim.x(), result.aim.y()};
    }
    private static void remember(InteractionContext context, Effector hand, Contact contact) {
        strm.touchnmotion.interaction.HandContacts.remember(context, INSTANCE.id(), hand, contact.point, contact.space, contact.normal);
    }

    /**
     * The arm aimed at the wall beside this shoulder, or {@code null} when there is none in reach.
     * {@code out} is the model x of this side.
     */
    private static Contact beside(AbstractClientPlayer player, IKFrame frame, Vector3f shoulder, float out) {
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
        return contact(player, frame, shoulder, palm, wall.normal);
    }

    /**
     * The arm aimed at the wall straight ahead of this shoulder, or {@code null} when there is none
     * in reach: the palm goes on the wall in front of the shoulder, as far down as makes an arm's
     * length.
     */
    private static Contact ahead(AbstractClientPlayer player, IKFrame frame, Vector3f shoulder) {
        Vec3 forward = horizontal(frame, new Vector3f(0, 0, -1));
        if (forward == null) return null;
        Vec3 from = frame.jointWorld(shoulder);
        Hit wall = wall(player, from, forward, LOOK_AHEAD);
        if (wall == null) return null;
        double arm = armLength(frame, shoulder);
        if (wall.distance > arm) return null;
        double below = Math.sqrt(arm * arm - wall.distance * wall.distance);
        Vec3 palm = from.add(forward.scale(wall.distance)).add(wall.normal.scale(PALM_OFF)).add(0, -below, 0);
        return contact(player, frame, shoulder, palm, wall.normal);
    }

    private record Hit(double distance, Vec3 normal) {
    }

    /** A wall face square to {@code direction} within {@code range} of {@code from}. */
    private static Hit wall(AbstractClientPlayer player, Vec3 from, Vec3 direction, double range) {
        Vec3 to = from.add(direction.scale(range));
        var hit = WallSurface.clip(player, from, to, SubLevels.around(player.level(), new AABB(from, to).inflate(0.2)));
        if (hit == null) return null;
        Vec3 normal = hit.normal();
        // Square to the shoulder, not a corner glanced at an angle.
        if (normal.dot(direction) > -0.7) return null;
        return new Hit(hit.position().subtract(from).dot(direction), normal);
    }

    private static Contact contact(AbstractClientPlayer player, IKFrame frame, Vector3f shoulder,
                                    Vec3 palm, Vec3 normal) {
        Vec3 from = palm.add(normal.scale(0.12)), to = palm.subtract(normal.scale(0.12));
        var face = WallSurface.clip(player, from, to, SubLevels.around(player.level(), new AABB(from, to).inflate(0.2)));
        if (face == null || face.normal().dot(normal) < 0.7) return null;
        Vec3 point = face.position().add(face.normal().scale(PALM_OFF));
        IKResult aim = aim(frame, shoulder, point);
        return aim == null ? null : new Contact(aim, point, face.normal(), face.space(),
                new strm.touchnmotion.interaction.ContactTarget(face.space(), face.block(),
                        player.level().getBlockState(face.block()).getBlock()));
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
}
