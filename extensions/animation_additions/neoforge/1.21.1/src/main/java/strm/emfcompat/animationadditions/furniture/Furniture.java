package strm.emfcompat.animationadditions.furniture;

import static strm.emfcompat.animationadditions.interaction.Skeleton.LEFT_SHOULDER;
import static strm.emfcompat.animationadditions.interaction.Skeleton.RIGHT_SHOULDER;
import strm.emfcompat.animationadditions.interaction.Skeleton;
import strm.emfcompat.animationadditions.interaction.Visibility;
import strm.emfcompat.animationadditions.interaction.HandContacts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import strm.emfcompat.animationadditions.interaction.SubLevels;
import org.joml.Vector3f;
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
 * Hands on a lectern and on a chest: looking at one close by, both hands go to it.
 *
 * <ul>
 *   <li>A lectern, from the side it is read from: the hands on the edges of the open book.</li>
 *   <li>A chest (a trapped chest, a double chest, an ender chest): the hands on the front edge of
 *   the lid, and as the lid opens they go up with its edge - the lid's angle is the one the game
 *   draws it at, so they lift it. Open, as the copper golem goes through a chest: the left hand
 *   holds the lid up, the right one is on the front edge of the chest's body.</li>
 * </ul>
 */
public final class Furniture implements InteractionProvider {

    public static final Furniture INSTANCE = new Furniture();
    public static final String KEY_ENABLED = "furniture.enabled";

    /** Below a button press and a door. */
    private static final int PRIORITY = 3;
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.15, 0.2, 0.06);

    private static final float ARM = Skeleton.ARM_TO_FINGERTIPS;
    /** As a share of the arm: a hand still goes to a grip a little past its length. */
    private static final float MAX_REACH = 1.6f;
    /** The same with a chest's lid all the way open. */
    private static final float OPEN_REACH = 2.6f;
    /** Further behind the shoulder than this, pixels, and the hand does not go. */
    private static final float MAX_BEHIND = 4f;

    private static final double RANGE = 2.0;
    private static final double LOOK_CONE = Math.cos(Math.toRadians(35));
    /** Faster than this, blocks per tick, the player walks past. */
    private static final double SLOW_BELOW = 0.1;

    /** Pixels. A chest's lid: the hinge at the back, this high; the front edge this far forward of it. */
    private static final double LID_HINGE_Y = 9;
    private static final double LID_DEPTH = 14;
    /** The front edge's grip is this far up the lid's front from the hinge's height, and this far out of it. */
    private static final double LID_EDGE_UP = 2.5;
    private static final double LID_OUT = 1;
    /** Open this far (0..1), the right hand goes from the lid to the chest's body. */
    private static final float OPEN_FROM = 0.5f;
    /** Pixels: the top of the chest's body, under the lid. */
    private static final double RIM_Y = 9;
    /** Each hand this far from the middle of the chest, sideways. */
    private static final double CHEST_SPREAD = 4.5;
    /** A lectern's book: height of its middle, how far it is towards the reader, the hands apart. */
    private static final double BOOK_Y = 13.5;
    private static final double BOOK_TOWARDS = 1;
    private static final double BOOK_SPREAD = 5.5;

    private Furniture() {
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Lecterns and chests", true,
                "On", "Looking at a lectern or a chest close by, both hands go to it; they lift a chest's lid as it opens.",
                "Off", "Leave the arms to EMF.");
    }

    @Override
    public String id() {
        return "Furniture";
    }

    @Override
    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    /** Where the hands go and how far they may reach. */
    private record Grips(String what, Vec3 right, Vec3 left, float reach, SubLevels.Space space,Object target) {
        Grips(String what,Vec3 right,Vec3 left,float reach){this(what,right,left,reach,SubLevels.WORLD,null);}
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        if (!player.onGround() || player.isPassenger() || player.isSleeping()
                || (player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING)) {
            context.decide("off:state");
            return;
        }
        if (Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) > SLOW_BELOW) {
            context.decide("off:moving");
            return;
        }
        Grips grips = look(player);
        if (grips == null) {
            context.decide("none");
            return;
        }
        IKFrame frame = context.frame();
        IKResult right = aim(frame, RIGHT_SHOULDER, grips.right, grips.reach);
        IKResult left = aim(frame, LEFT_SHOULDER, grips.left, grips.reach);
        if (right != null) HandContacts.remember(context, id(), Effector.RIGHT_ARM, grips.right, grips.space);
        if (left != null) HandContacts.remember(context, id(), Effector.LEFT_ARM, grips.left, grips.space);
        if (right != null) out.add(Candidate.single(id(), Category.USE, PRIORITY, 1f, TIMING, Effector.RIGHT_ARM,
                new float[]{right.x(), right.y()}).withTarget(grips.target));
        if (left != null) out.add(Candidate.single(id(), Category.USE, PRIORITY, 1f, TIMING, Effector.LEFT_ARM,
                new float[]{left.x(), left.y()}).withTarget(grips.target));
        context.decide(right == null && left == null ? grips.what + ":out-of-reach" : grips.what);
    }

    private static IKResult aim(IKFrame frame, Vector3f shoulder, Vec3 grip, float reach) {
        if (frame.relativeToJoint(grip, shoulder).z > MAX_BEHIND) return null;
        IKResult aim = OneBoneIK.solveXY(frame, shoulder, grip, ARM, 0f, 0f);
        return aim == null || aim.reach() > reach ? null : aim;
    }

    /** The lectern or chest nearest the look, within the cone round it, and where the hands go. */
    private static Grips look(AbstractClientPlayer player) {
        Level level = player.level();
        Grips best = null;
        double bestDot = LOOK_CONE;
        int r = (int) Math.ceil(RANGE);
        // The world, and the plot of a craft near by, each in its own coordinates (see SubLevels).
        for (SubLevels.Space space : SubLevels.around(level, player.getBoundingBox().inflate(r + 1))) {
            Vec3 at = space.toLocal(player.position());
            Vec3 eye = space.toLocal(player.getEyePosition());
            Vec3 view = space.directionToLocal(player.getViewVector(1f));
            BlockPos feet = BlockPos.containing(at);
            for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-r, -1, -r), feet.offset(r, 1, r))) {
                BlockState block = level.getBlockState(pos);
                boolean chest = block.getBlock() instanceof ChestBlock || block.getBlock() instanceof EnderChestBlock;
                if (!chest && !(block.getBlock() instanceof LecternBlock)) continue;
                Vec3 middle = Vec3.atCenterOf(pos);
                if (middle.distanceTo(at) > RANGE + 0.5) continue;
                double dot = middle.subtract(eye).normalize().dot(view);
                if (dot <= bestDot) continue;
                Grips grips = chest ? chest(at, view, level, pos.immutable(), block) : lectern(at, view, pos.immutable(), block);
                if (grips == null) continue;
                if (!Visibility.visible(player, pos, space.toWorld(middle))) continue;
                best = new Grips(grips.what, space.toWorld(grips.right), space.toWorld(grips.left), grips.reach,space,new strm.emfcompat.animationadditions.interaction.ContactTarget(space,pos,block.getBlock()));
                bestDot = dot;
            }
        }
        return best;
    }

    /** The hands on the edges of the book, from the side the lectern is read from. */
    private static Grips lectern(Vec3 at, Vec3 view, BlockPos pos, BlockState block) {
        Direction facing = block.getValue(LecternBlock.FACING);
        Vec3 towards = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 middle = new Vec3(pos.getX() + 0.5, pos.getY() + BOOK_Y / 16, pos.getZ() + 0.5);
        // Read from the side it faces.
        if (at.subtract(middle).dot(towards) <= 0) return null;
        Vec3 book = middle.add(towards.scale(BOOK_TOWARDS / 16));
        return hands("lectern", view, book, sideways(facing), BOOK_SPREAD, MAX_REACH);
    }

    /** The hands on the front edge of the lid, gone up with it as far as it is open. */
    private static Grips chest(Vec3 at, Vec3 view, Level level, BlockPos pos, BlockState block) {
        Direction facing = block.getValue(block.getBlock() instanceof ChestBlock ? ChestBlock.FACING : EnderChestBlock.FACING);
        Vec3 front = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 middle = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        // A double chest: the middle of the pair.
        if (block.getBlock() instanceof ChestBlock && block.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            middle = middle.add(Vec3.atLowerCornerOf(ChestBlock.getConnectedDirection(block).getNormal()).scale(0.5));
        }
        // From in front of it.
        if (at.subtract(middle).dot(front) <= 0) return null;
        float open = 0f;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof LidBlockEntity lid) {
            // As ChestRenderer draws it.
            float f = 1f - lid.getOpenNess(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
            open = 1f - f * f * f;
        }
        double angle = open * Math.PI / 2;
        double forward = LID_DEPTH * Math.cos(angle) - LID_EDGE_UP * Math.sin(angle);
        double up = LID_DEPTH * Math.sin(angle) + LID_EDGE_UP * Math.cos(angle);
        Vec3 hinge = middle.add(front.scale(-7.0 / 16)).add(0, LID_HINGE_Y / 16, 0);
        Vec3 edge = hinge.add(front.scale((forward + LID_OUT) / 16)).add(0, up / 16, 0);
        // Going up and back with the lid the edge leaves the hands' reach: they go with it further.
        float reach = MAX_REACH + (OPEN_REACH - MAX_REACH) * open;
        Grips lid = hands(open > 0.05f ? "chest-open" : "chest", view, edge, sideways(facing), CHEST_SPREAD, reach);
        if (open < OPEN_FROM) return lid;
        // Open, as the copper golem goes through a chest: the left hand holds the lid up, the right
        // one is on the front edge of the chest's body, on its own side.
        Vec3 side = sideways(facing);
        Vec3 playerRight = new Vec3(-view.z, 0, view.x);
        Vec3 rim = middle.add(0, RIM_Y / 16, 0).add(front.scale((7 + LID_OUT) / 16))
                .add(side.scale(Math.signum(side.dot(playerRight)) * CHEST_SPREAD / 16));
        return new Grips("chest-open", rim, lid.left, reach);
    }

    /** A horizontal direction square to {@code facing}. */
    private static Vec3 sideways(Direction facing) {
        return Vec3.atLowerCornerOf(facing.getClockWise().getNormal());
    }

    /** Two grips {@code spread} pixels either side of {@code at}, each to the hand on its side. */
    private static Grips hands(String what, Vec3 view, Vec3 at, Vec3 side, double spread, float reach) {
        Vec3 a = at.add(side.scale(spread / 16));
        Vec3 b = at.add(side.scale(-spread / 16));
        // The player's right: the look turned a quarter clockwise, seen from above.
        Vec3 right = new Vec3(-view.z, 0, view.x);
        return a.subtract(b).dot(right) > 0 ? new Grips(what, a, b, reach) : new Grips(what, b, a, reach);
    }
}
