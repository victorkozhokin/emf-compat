package strm.emfcompat.animationadditions.plantreach;

import static strm.emfcompat.animationadditions.interaction.Skeleton.LEFT_SHOULDER;
import static strm.emfcompat.animationadditions.interaction.Skeleton.RIGHT_SHOULDER;
import strm.emfcompat.animationadditions.interaction.Skeleton;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Hands brushing plants: standing in or walking through a field of crops, each hand reaches for
 * the top of a plant on its side, the way a hand trails over a wheat field. Only crops, unless set
 * to all plants - grass, ferns and flowers are underfoot everywhere, and the hands were at them
 * all the time.
 *
 * <p>All plants are bushes ({@link BushBlock}: grass, tall grass, ferns, flowers, crops, saplings, berry
 * bushes, mushrooms). Each arm is aimed with {@link OneBoneIK} at the point on the top of the
 * plant nearest to where its hand hangs - or as high as the arm reaches, for a plant taller than
 * that - on its side and not behind, so the hand follows the plants as they come closer and fall
 * behind. Of a plant the player stands in, only the part sticking out from under the body on the
 * hand's side counts: in a dense field or on the edge between two blocks the hands reached for the
 * feet.</p>
 *
 * <p>A passive-contact provider, one candidate per hand, below a wall in priority: an arm on a
 * wall stays on it, and the arbiter settles that - this class knows nothing about walls. The aim
 * is smoothed by the runtime, since the plant changes as the player walks.</p>
 */
public final class PlantReach implements InteractionProvider {

    public static final PlantReach INSTANCE = new PlantReach();
    private static final int PRIORITY = 10;
    /** The fade-out is short: the runtime lets a hand go over six of these, and past a field's end the hand hung in the air for a second. */
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.15, 0.06, 0.08);

    public static final String KEY_ENABLED = "plantreach.enabled", KEY_ALL = "plantreach.all";

    /** From the shoulder pivot to the palm, in pixels. */
    private static final float ARM = Skeleton.ARM_TO_PALM;
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

    private PlantReach() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Hands brush crops", true,
                "On", "Walking through a field of crops, the hands trail over them.",
                "Off", "Leave the arms to EMF.");
        config.addChild(KEY_ENABLED, KEY_ALL, "React to all plants", false,
                "On", "The hands reach for any plant beside you: grass, ferns, flowers, saplings and mushrooms as well as crops.",
                "Off", "Only crops: wheat, carrots, potatoes, beetroot and the like.");
    }

    @Override
    public String id() {
        return "PlantReach";
    }

    @Override
    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        String why = ineligible(player);
        if (why != null) {
            context.decide(why);
            return;
        }
        // Adjacent plants are one continuous brushing gesture. A per-block target would
        // repeatedly start the global pose handoff while the hand slides along a row.
        Selection selection = context.data(Selection::new);
        Map<BlockPos, AABB> plants = selection.plants(player, context.now());
        // Nothing growing within reach - by far the usual case - costs nothing more.
        Reach right = plants.isEmpty() ? null : reach(player, plants, context.frame(), RIGHT_SHOULDER, -1f, selection.right);
        Reach left = plants.isEmpty() ? null : reach(player, plants, context.frame(), LEFT_SHOULDER, 1f, selection.left);
        // Walking by the very edge of a field a plant is in reach for a frame or two and out again: the hand is not
        // sent out for those, and not called back for as short a gap.
        right = selection.steady[0].of(right, context.dt());
        left = selection.steady[1].of(left, context.dt());
        selection.right = right == null ? null : right.point;
        selection.left = left == null ? null : left.point;
        if (right != null) {
            if (right.box != null) strm.emfcompat.animationadditions.interaction.HandContacts.rememberPlant(context, Effector.RIGHT_ARM, right.point, right.box);
            else strm.emfcompat.animationadditions.interaction.HandContacts.forget(context, id(), Effector.RIGHT_ARM);
            out.add(Candidate.single(id(), Category.PASSIVE, PRIORITY, 0.5f, TIMING, Effector.RIGHT_ARM, right.aim));
        }
        if (left != null) {
            if (left.box != null) strm.emfcompat.animationadditions.interaction.HandContacts.rememberPlant(context, Effector.LEFT_ARM, left.point, left.box);
            else strm.emfcompat.animationadditions.interaction.HandContacts.forget(context, id(), Effector.LEFT_ARM);
            out.add(Candidate.single(id(), Category.PASSIVE, PRIORITY, 0.5f, TIMING, Effector.LEFT_ARM, left.aim));
        }
        if (strm.emfcompat.animationadditions.DebugLog.trace() && !plants.isEmpty() && selection.pace.due(500_000_000L)) {
            BlockPos in = BlockPos.containing(player.getX(), player.getY() + .3, player.getZ());
            Vec3 from = context.frame().jointWorld(new Vector3f(RIGHT_SHOULDER)), hanging = context.frame().jointWorld(new Vector3f(RIGHT_SHOULDER).add(0, ARM, 0));
            org.slf4j.LoggerFactory.getLogger("EMFCompatPlants").info("[PlantTrace] plants={} in={} inField={} box={} shoulder={} hanging={} arm={} right={} left={}",
                    plants.size(), in.toShortString(), plants.containsKey(in), plants.get(in), from, hanging, from.distanceTo(hanging), right != null, left != null);
        }
        if (strm.emfcompat.animationadditions.DebugLog.trace() && !plants.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger("EMFCompatPlants").info("[PlantPoint] at=({} {}) R={} L={} aimR={} aimL={}",
                    String.format("%.3f", player.getX()), String.format("%.3f", player.getZ()),
                    right == null ? "-" : String.format("%.3f %.3f %.3f", right.point.x, right.point.y, right.point.z),
                    left == null ? "-" : String.format("%.3f %.3f %.3f", left.point.x, left.point.y, left.point.z),
                    right == null ? "-" : String.format("%.3f %.3f", right.aim[0], right.aim[1]),
                    left == null ? "-" : String.format("%.3f %.3f", left.aim[0], left.aim[1]));
        }
        context.decide((right != null ? "R" : "-") + (left != null ? "L" : "-"));
    }

    private static String ineligible(AbstractClientPlayer player) {
        if (player.isPassenger() || player.isSleeping() || player.isInWaterOrBubble()) return "off:state";
        if (player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING) return "off:pose";
        return null;
    }

    /**
     * The arm aimed at a plant on this side, or {@code null} when there is none in reach.
     * {@code out} is the model x of this side.
     */
    private static final class Selection {
        Vec3 right, left;
        final Steady[] steady = {new Steady(), new Steady()};
        final strm.emfcompat.animationadditions.DebugLog.Pace pace = new strm.emfcompat.animationadditions.DebugLog.Pace();
        /** The plants round the player and where their tops are, looked up when the player changes block or this grows stale. */
        private final Map<BlockPos, AABB> plants = new HashMap<>();
        private BlockPos centre;
        private long lookedAt;

        Map<BlockPos, AABB> plants(AbstractClientPlayer player, long now) {
            BlockPos here = BlockPos.containing(player.getX(), player.getY() + .5, player.getZ());
            if (here.equals(centre) && now - lookedAt < STALE_NANOS) return plants;
            centre = here;
            lookedAt = now;
            plants.clear();
            Level level = player.level();
            boolean all = EMFCompatConfig.getBoolean(KEY_ALL, false);
            // Two blocks each way: a hand hangs up to a block from the body's middle and looks a block round itself.
            for (BlockPos pos : BlockPos.betweenClosed(here.offset(-2, -2, -2), here.offset(2, 2, 2))) {
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof BushBlock) || !all && !crop(state)) continue;
                VoxelShape shape = state.getShape(level, pos);
                if (!shape.isEmpty()) plants.put(pos.immutable(), shape.bounds().move(pos));
            }
            return plants;
        }
    }

    /** A crop: what is sown and reaped in a field. By its kind, and by the game's own list of crops, which a mod's crop is put on. */
    private static boolean crop(BlockState state) {
        return state.getBlock() instanceof net.minecraft.world.level.block.CropBlock || state.is(net.minecraft.tags.BlockTags.CROPS);
    }

    /** Seconds: a plant has to be in reach this long before the hand goes out to it, and out of reach this long before the hand is let fall. */
    private static final double DWELL_SECONDS = 0.12, LINGER_SECONDS = 0.1;

    /** One hand's reach, steadied: taken up only once it has lasted, and kept through a short gap as it last was against the body. */
    private static final class Steady {
        private Reach last;
        private double lasted, gone;
        private boolean out;

        Reach of(Reach now, double dt) {
            if (now != null) {
                lasted += dt;
                gone = 0;
                last = now;
            } else {
                gone += dt;
                if (!out) lasted = 0;
            }
            if (!out && lasted >= DWELL_SECONDS) out = true;
            if (out && gone > LINGER_SECONDS) {
                out = false;
                lasted = 0;
                last = null;
            }
            // Through a gap the arm's own aim is kept - against the body, so the hand goes on with the walker - and no plant is leant on.
            return !out ? null : now != null ? now : new Reach(last.aim, last.point, null);
        }
    }

    /** A plant broken or grown shows in the hands within this. */
    private static final long STALE_NANOS = 250_000_000L;
    private record Reach(float[] aim, Vec3 point, AABB box) {}
    private record Surface(Vec3 point, AABB box) {}

    private static Reach reach(AbstractClientPlayer player, Map<BlockPos, AABB> plants, IKFrame frame, Vector3f shoulder, float out, Vec3 previous) {
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
        // Standing in the plants themselves there is no row to slide a hand along: every block
        // around is one, and an edge picked block by block sends the hands from plant to plant.
        // There the hand goes to the nearest top beside the body, as it always did, and the
        // runtime's own smoothing carries it; the edge is for walking along the outside of a row.
        // (A little above the soles: farmland is lower than a block, and the crop is in the block over it.)
        // (A field of crops is not such a place: its top is one surface, and the hand lies on it out to the side the
        // same within the field as beside it - there is no break as the walker steps in or out.)
        BlockPos standing = BlockPos.containing(player.getX(), player.getY() + .3, player.getZ());
        AABB under = plants.get(standing);
        if (under != null && !fills(standing, under)) {
            Vec3 point = nearestInField(player, plants, from, hanging, centre, side, ahead, lowest, highest);
            if (point == null || point.distanceTo(from) > arm) return null;
            IKResult result = OneBoneIK.solveXY(frame, shoulder, point, ARM, 0f, 0f);
            return result == null ? null : new Reach(new float[]{result.x(), result.y()}, point, null);
        }
        Surface surface = nearest(player, plants, from, hanging, centre, side, ahead, lowest, highest, previous);
        if (surface == null) return null;
        Vec3 best = surface.point;
        if (best.distanceTo(from) > arm + 1e-6) return null;
        IKResult result = OneBoneIK.solveXY(frame, shoulder, best, ARM, 0f, 0f);
        return result == null ? null : new Reach(new float[]{result.x(), result.y()}, best, surface.box);
    }

    /**
     * The point on a plant's top nearest to where the hand hangs, on its side, ahead or beside but
     * not behind, and not under the body.
     */
    private static Surface nearest(AbstractClientPlayer player, Map<BlockPos, AABB> plants, Vec3 from, Vec3 hanging, Vec3 centre,
                                Vec3 side, Vec3 ahead, double lowest, double highest, Vec3 previous) {
        AABB footprint = player.getBoundingBox().inflate(BODY_MARGIN, 0, BODY_MARGIN);
        BlockPos feet = BlockPos.containing(hanging);
        Surface best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 1, 1))) {
            AABB box = plants.get(pos);
            if (box == null) continue;
            // A field is one top, not a block of it after another: taken plant by plant, the hand's place stuck at
            // each seam between two of them and now and then was thrown ahead to the next.
            boolean whole = fills(pos, box);
            box = field(plants, pos, box);
            // The point of the plant's top nearest the hand, no higher than the arm can bring it.
            double y = Math.min(highest, box.maxY - INTO_TOP);
            if (y < lowest || y < box.minY) continue;
            // A straight arm cannot stop halfway down its length. Pick a real point
            // on the canopy at arm length, rather than aiming through the nearby plant.
            // Brush the nearest outside edge, sliding parallel to the walking player.
            // Fixing canopy height first instead made the hand orbit each plant and trail behind.
            var edge = CanopyContact.edge(from.x, from.y, from.z, from.distanceTo(hanging),
                    box.minX, box.maxX, box.minY, Math.min(highest, box.maxY - INTO_TOP), box.minZ, box.maxZ);
            List<CanopyContact.Point> contacts = List.of();
            // A field's top is one surface: the hand lies on it an arm's length out to the side, and slides in over it
            // as the walker comes nearer. Its rim was the rule here once, and left a strip beside the field - the
            // shoulder all but over the rim - where the rim's place was under the body and the hand had nowhere to go.
            if (whole) {
                double reach = from.distanceTo(hanging), flat = reach * reach - (from.y - y) * (from.y - y);
                if (flat >= 0) {
                    double px = from.x + side.x * Math.sqrt(flat), pz = from.z + side.z * Math.sqrt(flat);
                    if (px > box.minX && px < box.maxX && pz > box.minZ && pz < box.maxZ) contacts = List.of(new CanopyContact.Point(px, pz));
                }
            }
            if (whole && contacts.isEmpty()) {
                // Past the field's end or too far from it there is no such place, and its rim is no stand-in for one:
                // the hand is let fall, not swung round to the corner.
                continue;
            }
            if (!contacts.isEmpty()) {
                // Straight out to the side, on the top.
            } else if (edge != null) {
                y = edge.y();
                contacts = List.of(new CanopyContact.Point(edge.x(), edge.z()));
            } else contacts = CanopyContact.points(from.x, from.z, from.y - y, from.distanceTo(hanging),
                    box.minX + 1e-5, box.maxX - 1e-5, box.minZ + 1e-5, box.maxZ - 1e-5,
                    hanging.x + side.x * .3, hanging.z + side.z * .3);
            for (var candidate : contacts) {
                Vec3 point = new Vec3(candidate.x(), y, candidate.z());
                if (footprint.contains(point.x, (footprint.minY + footprint.maxY) * .5, point.z)) continue;
                if (point.subtract(centre).dot(side) < ACROSS || point.subtract(from).dot(ahead) < -BEHIND) continue;
                double distance = point.distanceTo(hanging);
                if (previous != null) distance += point.distanceTo(previous) * .05;
                // A hand trailing over a field leans on nothing: no contact is kept for it, so no stance is taken for it and
                // the arm is not shifted at the shoulder to meet a point - both showed, the arm off the body and the body
                // changing its stand in a frame as the second hand came onto the field.
                if (distance < bestDistance) { best = new Surface(point, whole ? null : box); bestDistance = distance; }
            }
        }
        return best;
    }

    /** Blocks each way a field's top is followed from a plant; the hand looks no further. */
    private static final int FIELD_REACH = 3;

    /**
     * The top of the field the plant at {@code pos} is part of: its own box grown over the plants
     * next to it that fill their blocks side to side and stand as high - first along one way, then
     * the other for as long as the whole strip is plants. A plant that does not fill its block
     * (a tuft of grass, a flower) is left as it is.
     */
    private static AABB field(Map<BlockPos, AABB> plants, BlockPos pos, AABB box) {
        if (!fills(pos, box)) return box;
        int minZ = pos.getZ(), maxZ = pos.getZ(), minX = pos.getX(), maxX = pos.getX();
        while (pos.getZ() - minZ < FIELD_REACH && same(plants, new BlockPos(pos.getX(), pos.getY(), minZ - 1), box)) minZ--;
        while (maxZ - pos.getZ() < FIELD_REACH && same(plants, new BlockPos(pos.getX(), pos.getY(), maxZ + 1), box)) maxZ++;
        while (pos.getX() - minX < FIELD_REACH && strip(plants, minX - 1, pos.getY(), minZ, maxZ, box)) minX--;
        while (maxX - pos.getX() < FIELD_REACH && strip(plants, maxX + 1, pos.getY(), minZ, maxZ, box)) maxX++;
        return new AABB(minX, box.minY, minZ, maxX + 1, box.maxY, maxZ + 1);
    }

    private static boolean strip(Map<BlockPos, AABB> plants, int x, int y, int minZ, int maxZ, AABB like) {
        for (int z = minZ; z <= maxZ; z++) {
            if (!same(plants, new BlockPos(x, y, z), like)) return false;
        }
        return true;
    }

    private static boolean same(Map<BlockPos, AABB> plants, BlockPos pos, AABB like) {
        AABB box = plants.get(pos);
        return box != null && fills(pos, box) && Math.abs(box.maxY - like.maxY) < 0.07;
    }

    private static boolean fills(BlockPos pos, AABB box) {
        return box.minX <= pos.getX() + 1e-3 && box.maxX >= pos.getX() + 1 - 1e-3 && box.minZ <= pos.getZ() + 1e-3 && box.maxZ >= pos.getZ() + 1 - 1e-3;
    }

    /** In a field: the point on a plant's top nearest to where the hand hangs, beside the body. */
    private static Vec3 nearestInField(AbstractClientPlayer player, Map<BlockPos, AABB> plants, Vec3 from, Vec3 hanging, Vec3 centre,
                                Vec3 side, Vec3 ahead, double lowest, double highest) {
        AABB footprint = player.getBoundingBox().inflate(BODY_MARGIN, 0, BODY_MARGIN);
        BlockPos feet = BlockPos.containing(hanging);
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 1, 1))) {
            AABB box = plants.get(pos);
            if (box == null) continue;
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
}
