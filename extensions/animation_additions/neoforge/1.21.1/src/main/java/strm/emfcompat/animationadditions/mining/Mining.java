package strm.emfcompat.animationadditions.mining;

import static strm.emfcompat.animationadditions.interaction.Skeleton.LEFT_SHOULDER;
import static strm.emfcompat.animationadditions.interaction.Skeleton.RIGHT_SHOULDER;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.Category;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.animationadditions.interaction.InteractionProvider;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.interaction.Skeleton;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.animationadditions.mixin.LevelRendererAccessor;
import strm.emfcompat.animationadditions.torso.BraceSteps;
import strm.emfcompat.animationadditions.torso.LowReach;
import strm.emfcompat.animationadditions.torso.TorsoLean;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKMath;

import java.util.List;
import java.util.Map;
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
 * strikes ({@link ToolSwing#windUp}): from the point up, each tool its own way and a pickaxe and
 * an axe another way at random with every blow, and down onto the point again.</p>
 *
 * <p>The item's shape comes from how the game holds a handheld item: its handle across the palm,
 * the item pointing forward and a little down from the hand. Nearer than the tip, the part of the
 * item at that distance touches instead; further, it strikes towards the point, short of it.</p>
 *
 * <p>The body works with the arm ({@link MiningBody}): standing still, the feet step apart for the
 * tool and stay so while the breaking goes on; the torso is held turned and bent to the point and
 * pulses with every swing; a point the tool is short of brings the pelvis after it.</p>
 */
public final class Mining implements InteractionProvider {

    public static final Mining INSTANCE = new Mining();
    public static final String KEY_ENABLED = "mining.enabled";

    /** Above a button press: breaking a block, the hand is the swing's. */
    private static final int PRIORITY = 10;
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.08, 0.25, 0.05);


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

    /** Only held over a block, not breaking it, the arm is this much of the way wound up, and gets there this fast, seconds. */
    private static final float HELD = 0.45f;
    private static final double HELD_SECONDS = 0.3;
    /**
     * The torso follows what is asked of it a little late; the pulse is asked for this much of a
     * swing ahead, so the body comes down with the blow and not after it.
     */
    private static final float BODY_LEAD = 0.3f;
    /** How fast the body takes up and gives up its part, seconds. */
    private static final double BODY_SECONDS = 0.18;
    /** After anyone last mined: the longest the body's part can still be going. */
    private static final long BUSY_NANOS = 5_000_000_000L;
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatMining");
    private static final Vector3f HOME = new Vector3f();
    /** The most the pelvis reach bends the body after a low point. */
    private static final float LOW_REACH = (float) Math.toRadians(12);
    private static long busyAt = System.nanoTime() - BUSY_NANOS;

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
        ToolSwing.Tool tool = ToolSwing.PICKAXE;
        /** When this player last broke at a block. */
        long minedAt;
        /** Which of its ways this blow is struck, and the one before; a pickaxe and an axe have several. */
        int blow, lastBlow;
        boolean breaking;
        /** How far wound up the arm stays between swings, 0..1: not at all while breaking, eased up while only held over a block. */
        float held;
        /** How much of the body's part shows, 0..1: in and out slower than the arm. */
        float effort;
        AbstractClientPlayer player;
        IKFrame frame;
        final BraceSteps.State stance = new BraceSteps.State();
        final LowReach.State low = new LowReach.State();

        {
            low.angleLimit = LOW_REACH;
        }
    }

    public static void register(ConfigRegistry.Group config) {
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
        state.player = player;
        state.frame = context.frame();
        state.effort += ((state.active ? 1f : 0f) - state.effort) * Smoothing.follow(context.dt(), BODY_SECONDS);
        state.held += ((state.breaking ? 0f : HELD) - state.held) * Smoothing.follow(context.dt(), HELD_SECONDS);
        if (state.active) busyAt = now;
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
        state.breaking = breaking;
        ToolSwing.Tool tool = tool(player.getMainHandItem());
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
        SubLevels.Space space = SubLevels.at(player.level(), pos);
        Vec3 point = space.toLocal(point(player, pos, partial));
        if (!state.active || !space.same(state.space)) state.point = state.aimed = point;
        else if (!pos.equals(state.pos) || phase < state.phase - NEW_SWING || !breaking && phase <= 0f) state.aimed = point;
        state.space = space;
        state.point = state.point.lerp(state.aimed, Smoothing.follow(context.dt(), POINT_SECONDS));
        // Where it is on the model this frame: the player may walk or turn meanwhile.
        state.hit.set(frame.relativeToJoint(state.space.refresh().toWorld(state.point), new Vector3f()));
        // A swing ending drops the value to nothing as well: only one that goes on is a new blow.
        if (state.active && player.swinging && phase < state.phase - NEW_SWING) {
            // Another of the three, never the same twice running.
            state.lastBlow = state.blow;
            state.blow = (state.blow + 1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(2)) % 3;
        }
        state.active = true;
        state.pos = pos;
        state.phase = phase;
        state.right = player.getMainArm() == HumanoidArm.RIGHT;
        state.tool = tool;

        Vector3f to = new Vector3f(state.hit).sub(state.right ? RIGHT_SHOULDER : LEFT_SHOULDER);
        float[] aim = ToolSwing.solve(to, state.tool);
        out.add(Candidate.single(id(), Category.ACTIVE, PRIORITY, 1f, TIMING,
                state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, new float[]{aim[0], aim[1]}).withTarget(
                new strm.emfcompat.animationadditions.interaction.ContactTarget(SubLevels.at(player.level(), pos), pos, player.level().getBlockState(pos).getBlock())));
        // The swing is the strike.
        context.claimArms();
        context.decide((breaking ? "mine:" : "hover:") + state.tool.name() + (aim[2] > 1.05f ? ":short" : ":contact"));
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
        // Their own game says it outright; the crack list is the guess for a server that passes nothing on.
        if (strm.emfcompat.animationadditions.net.Inputs.told(player))
            return strm.emfcompat.animationadditions.net.Inputs.attackHeld(player) && strm.emfcompat.animationadditions.net.Inputs.sight(player, 6, 1f) instanceof BlockHitResult told
                    && told.getType() == HitResult.Type.BLOCK ? told.getBlockPos() : null;
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
                ? mc.hitResult : strm.emfcompat.animationadditions.net.Inputs.sight(player, range, partial);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
    }

    /** Where the player's look meets the block; its middle when the look misses it. */
    private static Vec3 point(AbstractClientPlayer player, BlockPos pos, float partial) {
        // In the block's own space: on a craft its plot, carried out to where it is drawn.
        SubLevels.Space space = SubLevels.at(player.level(), pos);
        Vec3 eye = space.toLocal(player.getEyePosition(partial));
        Vec3 end = eye.add(space.directionToLocal(player.getViewVector(partial)).scale(player.blockInteractionRange() + 1));
        VoxelShape shape = player.level().getBlockState(pos).getShape(player.level(), pos);
        // On the shape itself: a slab, a stair or a fence is hit where it is, not on the box round it.
        BlockHitResult on = shape.isEmpty() ? null : shape.clip(eye, end, pos);
        if (on != null) return space.toWorld(on.getLocation());
        AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
        return space.toWorld(box.clip(eye, end).orElse(box.getCenter()));
    }

    /** The tool in the hand; {@code null} for anything else - only tools swing onto the block. */
    private static ToolSwing.Tool tool(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof PickaxeItem) return ToolSwing.PICKAXE;
        if (item instanceof AxeItem) return ToolSwing.AXE;
        if (item instanceof ShovelItem) return ToolSwing.SHOVEL;
        if (item instanceof HoeItem) return ToolSwing.HOE;
        return null;
    }

    /**
     * Puts the item on the point from where the shoulder is drawn this frame, wound up as far as
     * the swing is, as much as the arm is this provider's. Called last, after the runtime;
     * {@code base} is the arm as it was before the runtime aimed it, so the weight is taken once.
     */
    public static void aimArm(UUID uuid, Function<String, ModelPart> parts, Map<Effector, float[]> base) {
        State state = STATES.fresh(uuid);
        // Let go of, it keeps the last pose while the runtime fades it out.
        if (state == null) return;
        Effector effector = state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM;
        float w = InteractionRuntime.weight(uuid, effector, INSTANCE.id());
        if (w < 1e-3f) return;
        ModelPart arm = parts.apply(effector.part);
        if (arm == null) return;
        // A swing starts on the point, as the last blow held the tool, and goes up to this blow's
        // wound-up pose; how the tool is held on the point changes at the top, where none of that shows.
        Quaternionf strike = ToolSwing.strike(new Vector3f(state.hit).sub(arm.x, arm.y, arm.z), state.tool, state.right,
                ToolSwing.time(state.phase) < ToolSwing.TOP ? state.lastBlow : state.blow);
        float wound = ToolSwing.windUp(state.phase);
        Quaternionf q = new Quaternionf(strike).slerp(ToolSwing.wound(state.tool, state.right, state.blow, strike), wound + (1 - wound) * state.held);
        Vector3f euler = ToolSwing.zyx(q);
        float[] from = base == null ? null : base.get(effector);
        if (from == null) from = new float[]{arm.xRot, arm.yRot, arm.zRot};
        arm.xRot = from[0] + IKMath.wrap(euler.x - from[0]) * w;
        arm.yRot = from[1] + IKMath.wrap(euler.y - from[1]) * w;
        arm.zRot = from[2] + IKMath.wrap(euler.z - from[2]) * w;
    }

    private static boolean quiet() {
        return System.nanoTime() - busyAt > BUSY_NANOS;
    }

    /** How much of the body's part shows: only as far as the arm is this provider's. */
    private static float shown(UUID uuid, State state) {
        if (state.effort < 1e-3f || !INSTANCE.isEnabled()) return 0;
        return state.effort * InteractionRuntime.weight(uuid, state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, INSTANCE.id());
    }

    public static TorsoLean.Hint torsoHint(UUID uuid) {
        if (quiet()) return null;
        State state = STATES.fresh(uuid);
        if (state == null) return null;
        float shown = shown(uuid, state);
        if (shown < 1e-3f) return null;
        float ahead = ToolSwing.time(state.phase) + BODY_LEAD;
        float[] lean = MiningBody.lean(state.tool, state.right, state.hit.y,
                state.breaking ? ToolSwing.curve(ahead - (float) Math.floor(ahead)) : .5f);
        return TorsoLean.Hint.turn(lean[0] * shown, lean[1] * shown, lean[2] * shown);
    }

    /** The feet, before the torso: a step each to the tool's stance, held while breaking, a step each back. */
    public static void support(UUID uuid, Function<String, ModelPart> parts) {
        if (quiet()) return;
        State state = STATES.fresh(uuid);
        if (state == null || state.player == null) return;
        float shown = shown(uuid, state);
        float held = state.active ? shown : state.stance.resting() ? 0 : 1;
        if (held < .05f && state.stance.resting()) return;
        Vector3f[] feet = MiningBody.feet(state.tool, state.right);
        BraceSteps.apply(state.stance, state.player, state.frame, parts, state.active ? feet[0] : HOME,
                state.active ? feet[1] : HOME, held, 0, LOGGER, "MiningStance");
    }

    /**
     * After the torso: a point down by the legs that the tool is short of from where the shoulder
     * has come to brings the pelvis after it, the soles staying where they are - as far as a
     * moderate bend goes; a block out of reach is struck towards, not lunged at. Standing still only.
     */
    public static void reach(UUID uuid, Function<String, ModelPart> parts) {
        if (quiet()) return;
        State state = STATES.fresh(uuid);
        if (state == null || state.player == null) return;
        ModelPart arm = parts.apply(state.right ? "right_arm" : "left_arm");
        if (arm == null) return;
        float weight = 0;
        Vector3f target = new Vector3f(state.hit);
        boolean still = state.player.onGround() && !state.player.isPassenger()
                && state.player.getDeltaMovement().horizontalDistanceSqr() < .0004;
        if (state.active && still) {
            Vector3f to = new Vector3f(state.hit).sub(arm.x, arm.y, arm.z);
            float reach = (float) Math.hypot(state.tool.tipY(), state.tool.tipZ()), distance = to.length();
            weight = MiningBody.shortBy(distance, reach) * MiningBody.low(state.hit.y) * shown(uuid, state);
            // The fit is for a bare hand: ask it for the point less what the tool adds to the arm.
            if (distance > 1e-3f) target.sub(to.mul(Math.max(0, reach - Skeleton.ARM_TO_FINGERTIPS) / distance));
        }
        if (weight < 1e-3f && !state.low.active()) return;
        LowReach.apply(parts, state.right, target, weight, state.low);
    }

}
