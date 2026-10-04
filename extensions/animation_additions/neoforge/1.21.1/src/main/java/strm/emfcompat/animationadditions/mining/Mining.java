package strm.emfcompat.animationadditions.mining;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import strm.emfcompat.animationadditions.interaction.SubLevels;
import net.minecraft.world.phys.shapes.VoxelShape;
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
import strm.emfcompat.animationadditions.mixin.LevelRendererAccessor;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKMath;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Mining: while a player breaks a block with a tool - a pickaxe, an axe, a shovel, a hoe - the main
 * hand's swing brings its working part onto the point of the face that is hit. For
 * {@link #HOVER_SECONDS} after, between hits, a block looked at close by keeps the arm wound up.
 *
 * <p>Who breaks what is the level renderer's list of blocks being broken, which has the local player
 * and everyone watched. The point is where the player's look meets the block, taken again at the
 * start of each swing, so a strike lands where it was aimed. Each swing winds the arm up and
 * strikes: wound up at the start, each tool its own way ({@link Tool}), on the point at
 * {@link #IMPACT} of the swing, back up after.</p>
 *
 * <p>The item's shape comes from how the game holds a handheld item: its handle across the palm,
 * the item pointing forward and a little down from the hand. Nearer than the tip, the part of the
 * item at that distance touches instead; further, it strikes towards the point, short of it.</p>
 */
public final class Mining implements InteractionProvider {

    public static final Mining INSTANCE = new Mining();
    public static final String KEY_ENABLED = "mining.enabled";

    /** Above a button press: breaking a block, the hand is the swing's. */
    private static final int PRIORITY = 10;
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.08, 0.25, 0.05);

    /** Model space: pixels, y down, facing -z. */
    private static final Vector3f RIGHT_SHOULDER = new Vector3f(-5f, 2f, 0f);
    private static final Vector3f LEFT_SHOULDER = new Vector3f(5f, 2f, 0f);

    /** When in the swing, 0..1, the item is on the point. */
    private static final float IMPACT = 0.4f;
    /** Looking at a block this close, blocks from the eyes, the arm stays wound up over it between hits. */
    private static final double HOVER_RANGE = 3.0;
    private static final double HOVER_KEEP = 0.5;
    /** That long after breaking at a block, seconds; not before the first hit. */
    private static final double HOVER_SECONDS = 3.0;
    /** How fast the point glides to where the look is, seconds. */
    private static final double POINT_SECONDS = 0.06;
    /** Not swung for this long, seconds, a player has stopped breaking. */
    private static final double SWING_GAP = 0.4;
    /** The swing going back further than this is a new one. */
    private static final float NEW_SWING = 0.2f;

    /**
     * A tool: where it is on the arm, pixels, in the arm's own space (the arm along +y from the
     * shoulder, -z forward) - the handle in the hand and the working part, from the handheld item
     * transform and the tool's sprite - and the right arm's wound-up pose, degrees, as rotations
     * (documentation.md §15.6). The swing goes from the wound-up pose to the strike and back.
     */
    private record Tool(String name, float gripY, float gripZ, float tipY, float tipZ,
                        float windX, float windY, float windZ) {
        Quaternionf wound(boolean right) {
            float side = right ? 1f : -1f;
            // Mirrored for the left arm: yRot and zRot change sign, xRot does not.
            return new Quaternionf().rotationZYX(side * (float) Math.toRadians(windZ),
                    side * (float) Math.toRadians(windY), (float) Math.toRadians(windX));
        }
    }

    /** Overhead: the arm high over the shoulder, a little out, the pick back over it clear of the head. */
    private static final Tool PICKAXE = new Tool("pickaxe", 8.7f, -1.3f, 11.3f, -9.3f, -145f, -12f, -20f);
    /** A chop from the side: the arm out to its side at the shoulder, the axe's head up and back. */
    private static final Tool AXE = new Tool("axe", 8.7f, -1.3f, 12f, -9.8f, -180f, 55f, -90f);
    /** A thrust: the hand drawn back low by the hip, the blade forward and down, driven in. */
    private static final Tool SHOVEL = new Tool("shovel", 8.7f, -1.3f, 10.5f, -11.9f, 35f, -5f, 10f);
    /** A short chop down: the arm up in front, lower than a pick, the blade over it. */
    private static final Tool HOE = new Tool("hoe", 8.7f, -1.3f, 11.3f, -9.3f, -150f, 20f, -15f);

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private Mining() {
    }

    private static final class State {
        boolean active;
        BlockPos pos;
        /** The point struck, model pixels. */
        final Vector3f hit = new Vector3f();
        /** The point struck, in the world. */
        Vec3 point = Vec3.ZERO;
        /** Where it glides to. */
        Vec3 aimed = Vec3.ZERO;
        SubLevels.Space space = SubLevels.WORLD;
        long swungAt;
        float phase;
        boolean right;
        Tool tool = PICKAXE;
        /** When this player last broke at a block. */
        long minedAt;
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Mining", true,
                "On", "Breaking a block with a tool, the swing brings its head onto the point hit.",
                "Off", "Leave the arms to EMF.");
    }

    @Override
    public String id() {
        return "Mining";
    }

    @Override
    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        State state = STATES.seen(player.getUUID(), context.now()).value;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        long now = context.now();
        if (state.active && state.pos != null && player.level().getBlockState(state.pos).isAir()) {
            state.active = false;
            state.minedAt = 0;
            context.decide("target-removed");
            return;
        }
        if (player.swinging) state.swungAt = now;
        BlockPos pos = breaking(player, state, now);
        boolean breaking = pos != null;
        if (breaking) state.minedAt = now;
        Tool tool = tool(player.getMainHandItem());
        // Between hits - for a while after breaking at a block - a block looked at close by keeps
        // the arm wound up over it. Further off to let go than to take it, so it does not flicker.
        if (pos == null && state.minedAt != 0 && (now - state.minedAt) / 1e9 < HOVER_SECONDS) {
            pos = looked(player, partial, state.active ? HOVER_RANGE + HOVER_KEEP : HOVER_RANGE);
        }
        if (tool == null) pos = null;
        // Crack progress can outlive the block; it must not keep a contact with air.
        boolean disappeared = pos != null && player.level().getBlockState(pos).isAir();
        if (disappeared) {
            pos = null;
            state.minedAt = 0; // Do not reacquire the floor through the removed block on the next frame.
        }
        if (pos == null || player.isSleeping()
                || (player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING)) {
            state.active = false;
            context.decide(disappeared ? "target-removed" : pos == null ? "none" : "off:pose");
            return;
        }
        float phase = player.getAttackAnim(partial);
        IKFrame frame = context.frame();
        // The point, in the world, is taken again at the start of each swing and for a new block;
        // only looking and not swinging, it follows the look. It glides there, never jumps.
        SubLevels.Space space=SubLevels.at(player.level(),pos);
        Vec3 point = space.toLocal(point(player, pos, partial));
        if (!state.active || !space.same(state.space)) state.point = state.aimed = point;
        else if (!pos.equals(state.pos) || phase < state.phase - NEW_SWING || !breaking && phase <= 0f) state.aimed = point;
        state.space=space;
        state.point = state.point.lerp(state.aimed, Smoothing.follow(context.dt(), POINT_SECONDS));
        // Where it is on the model this frame: the player may walk or turn meanwhile.
        state.hit.set(frame.relativeToJoint(state.space.refresh().toWorld(state.point), new Vector3f()));
        state.active = true;
        state.pos = pos;
        state.phase = phase;
        state.right = player.getMainArm() == HumanoidArm.RIGHT;
        state.tool = tool;

        Vector3f to = new Vector3f(state.hit).sub(state.right ? RIGHT_SHOULDER : LEFT_SHOULDER);
        float[] aim = solve(to, state.tool);
        out.add(Candidate.single(id(), Category.ACTIVE, PRIORITY, 1f, TIMING,
                state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, new float[]{aim[0], aim[1]}).withTarget(
                new strm.emfcompat.animationadditions.interaction.ContactTarget(SubLevels.at(player.level(),pos),pos,player.level().getBlockState(pos).getBlock())));
        // The swing is the strike.
        context.claimArms();
        context.decide((breaking ? "mine:" : "hover:") + state.tool.name + (aim[2] > 1.05f ? ":short" : ":contact"));
    }

    /**
     * The block this player is breaking, {@code null} when none. The list keeps a block a while after
     * the player stops, so it counts only while they swing, or the local player's game says so.
     */
    private static BlockPos breaking(AbstractClientPlayer player, State state, long now) {
        Minecraft mc = Minecraft.getInstance();
        // Local crack packets may be stale or absent before the first progress update.
        // The native destruction controller and crosshair are authoritative for our own player.
        if (player == mc.player) {
            return mc.gameMode != null && mc.gameMode.isDestroying()
                    && mc.hitResult instanceof BlockHitResult hit ? hit.getBlockPos() : null;
        }
        if ((now - state.swungAt) / 1e9 > SWING_GAP) return null;
        var blocks = ((LevelRendererAccessor) Minecraft.getInstance().levelRenderer).emfcompat$destroyingBlocks();
        BlockDestructionProgress progress = blocks.get(player.getId());
        return progress == null ? null : progress.getPos();
    }

    /** The block looked at within {@link #HOVER_RANGE} of the eyes, {@code null} when none. */
    private static BlockPos looked(AbstractClientPlayer player, float partial, double range) {
        // Our own player: the game's crosshair target, which sees a craft's blocks (Sable) as a pick does not.
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = player == mc.player && mc.hitResult instanceof BlockHitResult own
                && SubLevels.toWorld(player.level(), own.getBlockPos(), own.getLocation())
                        .distanceTo(player.getEyePosition(partial)) <= range + 1e-3
                ? mc.hitResult : player.pick(range, partial, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
    }

    /** Where the player's look meets the block; its middle when the look misses it. */
    private static Vec3 point(AbstractClientPlayer player, BlockPos pos, float partial) {
        // In the block's own space: on a craft its plot, carried out to where it is drawn.
        SubLevels.Space space = SubLevels.at(player.level(), pos);
        Vec3 eye = space.toLocal(player.getEyePosition(partial));
        Vec3 end = eye.add(space.directionToLocal(player.getViewVector(partial)).scale(player.blockInteractionRange() + 1));
        VoxelShape shape = player.level().getBlockState(pos).getShape(player.level(), pos);
        AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
        return space.toWorld(box.clip(eye, end).orElse(box.getCenter()));
    }

    /** The tool in the hand; {@code null} for anything else - only tools swing onto the block. */
    private static Tool tool(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof PickaxeItem) return PICKAXE;
        if (item instanceof AxeItem) return AXE;
        if (item instanceof ShovelItem) return SHOVEL;
        if (item instanceof HoeItem) return HOE;
        return null;
    }

    /**
     * The arm's {xRot, yRot} putting the item on a point {@code to} pixels from the shoulder, and how
     * far the point is as a share of the item's tip. The part of the item as far from the shoulder
     * as the point is aimed at it - the tip, when the point is further.
     */
    private static float[] solve(Vector3f to, Tool tool) {
        float distance = to.length();
        if (distance < 1e-3f) return new float[]{0f, 0f, 0f};
        float gripReach = (float) Math.hypot(tool.gripY, tool.gripZ);
        float tipReach = (float) Math.hypot(tool.tipY, tool.tipZ);
        float s = tipReach - gripReach < 1e-3f ? 1f
                : Mth.clamp((distance - gripReach) / (tipReach - gripReach), 0f, 1f);
        float y = Mth.lerp(s, tool.gripY, tool.tipY);
        float z = Mth.lerp(s, tool.gripZ, tool.tipZ);
        // The arm's rotation about x turns that part round by the same angle from where it hangs.
        float offset = (float) Math.atan2(z, y);
        float dy = Mth.clamp(to.y / distance, -1f, 1f);
        float x = -(float) Math.acos(dy) - offset;
        float yaw = (float) Math.atan2(-to.x, -to.z);
        return new float[]{x, yaw, distance / tipReach};
    }

    /** How far wound up the arm is, 0..1, at this point of the swing. */
    private static float windUp(float phase) {
        if (phase < IMPACT) {
            float k = (float) Math.cos(Math.PI / 2 * phase / IMPACT);
            return k * k;
        }
        float k = (float) Math.sin(Math.PI / 2 * (phase - IMPACT) / (1f - IMPACT));
        return k * k;
    }

    /**
     * Puts the item on the point from where the shoulder is drawn this frame, wound up as far as
     * the swing is, as much as the arm is this provider's. Called last, after the runtime.
     */
    public static void aimArm(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        // Let go of, it keeps the last pose while the runtime fades it out.
        if (state == null) return;
        Effector effector = state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM;
        float w = InteractionRuntime.weight(uuid, effector, INSTANCE.id());
        if (w < 1e-3f) return;
        ModelPart arm = parts.apply(effector.part);
        if (arm == null) return;
        float[] aim = solve(new Vector3f(state.hit).sub(arm.x, arm.y, arm.z), state.tool);
        Quaternionf strike = new Quaternionf().rotationZYX(0f, aim[1], aim[0]);
        Quaternionf q = strike.slerp(state.tool.wound(state.right), windUp(state.phase));
        Vector3f euler = zyx(q);
        arm.xRot += IKMath.wrap(euler.x - arm.xRot) * w;
        arm.yRot += IKMath.wrap(euler.y - arm.yRot) * w;
        arm.zRot += IKMath.wrap(euler.z - arm.zRot) * w;
    }

    /**
     * {xRot, yRot, zRot} of a part turned by {@code q}, for the part's R = Rz Ry Rx. Worked out
     * from the rotated axes: joml's own getEulerAnglesZYX gave a wrong pose here.
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
