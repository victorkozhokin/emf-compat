package strm.emfcompat.animationadditions.doorhold;

import static strm.emfcompat.animationadditions.interaction.Skeleton.LEFT_SHOULDER;
import static strm.emfcompat.animationadditions.interaction.Skeleton.RIGHT_SHOULDER;
import strm.emfcompat.animationadditions.interaction.Skeleton;
import strm.emfcompat.animationadditions.interaction.Visibility;
import strm.emfcompat.animationadditions.interaction.HandContacts;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import strm.emfcompat.animationadditions.interaction.SubLevels;
import org.joml.Vector3f;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.Category;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.animationadditions.interaction.InteractionProvider;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKResult;
import strm.emfcompat.core.ik.OneBoneIK;

import java.util.List;

/**
 * Hands on doors: coming up to a door, shut or open, the hand on each leaf's side goes to its
 * handle - both hands at a double door - without it having to be looked at. Opened or shut, the
 * hand goes round with the handle, pushing or pulling; going through, it holds the leaf.
 *
 * <p>Only near the doorway, and only a handle the arm reaches without turning back behind the
 * shoulder: past the door the hand lets go. The handle is on the side the player is on, so the
 * hand goes round the edge as they pass it.</p>
 */
public final class DoorHold implements InteractionProvider {

    public static final DoorHold INSTANCE = new DoorHold();
    public static final String KEY_ENABLED = "doorhold.enabled";

    /** Below a press: a hand going for a button leaves the door. */
    private static final int PRIORITY = 5;
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.15, 0.2, 0.15);

    /** Shoulder to fingertips, pixels; a held edge may be at the fingertips. */
    private static final float ARM = Skeleton.ARM_TO_FINGERTIPS;
    /**
     * As a share of the arm: past its length on purpose. A shut leaf stands at the far side of its
     * block, so from right up against it its handle is most of a block away; the hand goes to it
     * anyway, as far as it gets.
     */
    private static final float MAX_REACH = 1.9f;
    /** Further behind the shoulder than this, pixels, and the hand lets go. */
    private static final float MAX_BEHIND = 6f;
    /** How far from the middle of the door's block, along either axis, counts as at the doorway. */
    private static final double DOORWAY = 1.5;
    /** A door's handle: height over the bottom of the door, in from the free edge and out of the leaf, blocks. */
    private static final double DOOR_HANDLE = 1.0;
    private static final double DOOR_IN = 2 / 16.0;
    private static final double DOOR_OUT = 1 / 16.0;

    private DoorHold() {
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Hold doors", true,
                "On", "Near a door the hand on each leaf's side goes to its handle, and holds it going through.",
                "Off", "Leave the arms to EMF.");
    }

    @Override
    public String id() {
        return "DoorHold";
    }

    @Override
    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        if (!player.onGround() || player.isPassenger() || player.isSleeping()
                || (player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING)) {
            context.decide("off:state");
            return;
        }
        IKFrame frame = context.frame();
        IKResult right = null, left = null;
        Vec3 rightPoint = null, leftPoint = null;
        float rightReach = Float.MAX_VALUE, leftReach = Float.MAX_VALUE;
        // The world, and a craft's plot seen from where the player stands on it (see SubLevels).
        for (SubLevels.Space space : SubLevels.around(player.level(), player.getBoundingBox().inflate(1.5))) {
            Vec3 at = space.toLocal(player.position());
            BlockPos feet = BlockPos.containing(at.x, at.y + 1e-3, at.z);
            for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, 0, -1), feet.offset(1, 0, 1))) {
                BlockState block = player.level().getBlockState(pos);
                if (!(block.getBlock() instanceof DoorBlock door) || !door.type().canOpenByHand()
                        || block.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
                if (Math.abs(at.x - (pos.getX() + 0.5)) > DOORWAY || Math.abs(at.z - (pos.getZ() + 0.5)) > DOORWAY) continue;
                Vec3 grip = space.toWorld(doorGrip(player.level(), at, pos.immutable(), block));
                if (!Visibility.visible(player, pos, grip)) continue;
                boolean isRight = frame.relativeToJoint(grip, new Vector3f()).x < 0;
                Vector3f shoulder = isRight ? RIGHT_SHOULDER : LEFT_SHOULDER;
                if (frame.relativeToJoint(grip, shoulder).z > MAX_BEHIND) continue;
                IKResult aim = OneBoneIK.solveXY(frame, shoulder, grip, ARM, 0f, 0f);
                if (aim == null || aim.reach() > MAX_REACH) continue;
                if (isRight && aim.reach() < rightReach) {
                    right = aim;
                    rightPoint = grip;
                    rightReach = aim.reach();
                } else if (!isRight && aim.reach() < leftReach) {
                    left = aim;
                    leftPoint = grip;
                    leftReach = aim.reach();
                }
            }
        }
        if (rightPoint != null) HandContacts.remember(context, id(), Effector.RIGHT_ARM, rightPoint);
        if (leftPoint != null) HandContacts.remember(context, id(), Effector.LEFT_ARM, leftPoint);
        if (right != null) out.add(Candidate.single(id(), Category.USE, PRIORITY, 1f, TIMING, Effector.RIGHT_ARM,
                new float[]{right.x(), right.y()}));
        if (left != null) out.add(Candidate.single(id(), Category.USE, PRIORITY, 1f, TIMING, Effector.LEFT_ARM,
                new float[]{left.x(), left.y()}));
        context.decide(right != null && left != null ? "both" : right != null ? "right" : left != null ? "left" : "none");
    }

    /**
     * A door's handle: on the edge away from the hinges, a block up, on the player's side of the
     * leaf. The hinges are where the leaf closed and the leaf open meet, so it comes out right
     * for either hinge side and either way round, open or shut.
     */
    private static Vec3 doorGrip(Level level, Vec3 at, BlockPos pos, BlockState block) {
        AABB leaf = block.getShape(level, pos).bounds();
        AABB shut = block.setValue(DoorBlock.OPEN, false).getShape(level, pos).bounds();
        AABB open = block.setValue(DoorBlock.OPEN, true).getShape(level, pos).bounds();
        AABB hinge = shut.intersect(open);
        double hx = hinge.getCenter().x, hz = hinge.getCenter().z;
        boolean alongX = leaf.getXsize() > leaf.getZsize();
        // The far end of the leaf from the hinges, a little in from the edge.
        double x, z;
        if (alongX) {
            x = Math.abs(leaf.minX - hx) > Math.abs(leaf.maxX - hx) ? leaf.minX + DOOR_IN : leaf.maxX - DOOR_IN;
            z = leaf.getCenter().z;
        } else {
            z = Math.abs(leaf.minZ - hz) > Math.abs(leaf.maxZ - hz) ? leaf.minZ + DOOR_IN : leaf.maxZ - DOOR_IN;
            x = leaf.getCenter().x;
        }
        Vec3 grip = new Vec3(pos.getX() + x, pos.getY() + DOOR_HANDLE, pos.getZ() + z);
        // Out of the leaf on the player's side.
        double half = (alongX ? leaf.getZsize() : leaf.getXsize()) / 2 + DOOR_OUT;
        Vec3 normal = alongX ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
        double side = Math.signum(at.subtract(grip).dot(normal));
        return grip.add(normal.scale((side == 0 ? 1 : side) * half));
    }
}
