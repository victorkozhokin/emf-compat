package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.Minecraft;
import strm.emfcompat.animationadditions.buttonpress.ReachEnvelope;
import strm.emfcompat.animationadditions.interaction.SubLevels;
import strm.emfcompat.animationadditions.interaction.Visibility;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import strm.emfcompat.animationadditions.buttonpress.ButtonPress;
import strm.emfcompat.animationadditions.buttonpress.ReachPose;
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.Category;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.animationadditions.interaction.InteractionProvider;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.interaction.Seated;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKMath;
import strm.emfcompat.core.ik.IKResult;
import strm.emfcompat.core.ik.OneBoneIK;

import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Using a block by hand - a chiseled bookshelf's slot, and so on ({@link BlockTarget}): looking at
 * a spot the hand can use, the main hand goes to it and waits there; when the block changes the way
 * a hand changes it, the hand puts in (in and back out), takes out (from inside, out past the
 * front) or taps. Continuous grips can add a bounded torso reach over the pack pose.
 *
 * <p>By the look, as a click is: the spot looked at is the one used. The hand only goes where the
 * click would do something, and only within reach of the arm - no using a block from across the
 * room. The use itself is instant; the gesture plays out after it,
 * as a lever's hand follows the handle over.</p>
 */
public final class BlockUse implements InteractionProvider {

    public static final BlockUse INSTANCE = new BlockUse();
    public static final String KEY_ENABLED = "blockuse.enabled";

    private static final List<BlockTarget> TARGETS = List.of(new ChiseledShelf(), new Jukebox(), new Campfire(), new Vault(), new HandCrank(),
            new Composter(), new FlowerPot(), new RespawnAnchor(), new NoteBlock(), new Repeater(), new Comparator(),
            new DaylightDetector(), new Cake(), new Barrel(), new Candle(), new ValveHandle(), new SteeringWheel(),
            new CraftingTable(), new Stonecutter(), new Bell(), new FenceGate(), new Cauldron(), new Beehive(),
            new CandleCake(), new Tnt(), new Crafter(), new EnchantingTable(), new CartographyTable(),
            new ItemRest("com.simibubi.create.content.logistics.depot.DepotBlock", "getHeldItem", 13),
            new ItemDrain(), new Basin(), new BlazeBurner(), new ContraptionControls(),
            ItemRest.front("com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock", "getInventory"),
            ItemRest.front("com.simibubi.create.content.kinetics.deployer.DeployerBlock", "heldItem"),
            ItemRest.front("com.simibubi.create.content.logistics.packager.PackagerBlock", "heldBox"),
            new ItemRest("com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlock", "inventory", 12),
            ItemRest.front("dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlock", "getHeldItem"),
            ItemRest.front("dev.eriksonn.aeronautics.content.blocks.mounted_potato_cannon.MountedPotatoCannonBlock", "getInventory"),
            new Typewriter(),
            // The two for whatever is left of a block, in this order: its value boxes, then anywhere on it.
            new ValuePanel(), new Panel());

    /** Below a button press, above doors and chests. */
    private static final int PRIORITY = 8;
    /**
     * A grip held and turned - a crank, a valve, a steering wheel - above a button press (10): the
     * look chose it, while a lever near by is only found by the body and would take the hand off it.
     */
    private static final int HELD_PRIORITY = 12;
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.12, 0.18, 0.05);

    /** Model space: pixels, y down, facing -z. */
    private static final Vector3f RIGHT_SHOULDER = new Vector3f(-5f, 2f, 0f);
    private static final Vector3f LEFT_SHOULDER = new Vector3f(5f, 2f, 0f);
    private static final float ARM = 11f;
    /** Looked for this far along the look, blocks. */
    private static final double RANGE = 3.0;
    /** Past the arm's length, as a share of it: further and the hand does not go. */
    private static final float MAX_REACH = 2.5f;
    /** Faster than this, blocks per tick, the player walks past. */
    private static final double SLOW_BELOW = 0.15;

    /** Pixels out of the block: where the hand waits; how deep a put goes in; from where and to where a take pulls. */
    private static final float HOVER_OUT = 1.5f;
    private static final float PUT_IN = 3f;
    private static final float TAKE_FROM = -2f;
    private static final float TAKE_TO = 5f;
    private static final float TAP_IN = 1.5f;
    private static final double GESTURE_SECONDS = 0.35;
    private static final double GRIP_SECONDS = 0.05;
    /**
     * Levers held on a moving train: the player is carried by the train a tick at a time and the
     * train is drawn between ticks, so the levers shake against the body by that much. Against the
     * body they hardly move at all - followed this slowly, the shake is gone.
     */
    private static final double HELD_GRIP_SECONDS = 0.18;
    /**
     * The torso going with a hand that goes round ({@link BlockTarget#swayCentre}), radians at most:
     * forwards and back with the hand further and nearer, and lower; turned after it to either side.
     */
    private static final float SWAY_PITCH = (float) Math.toRadians(5);
    private static final float SWAY_YAW = (float) Math.toRadians(6);
    /** Pixels off the middle for the whole of it: about a crank's reach. */
    private static final float SWAY_RADIUS = 6f;

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);


    private BlockUse() {
    }

    private static final class State {
        /** The block looked at, as it was last seen, and its target. */
        BlockPos pos;
        Object last;
        BlockTarget target;
        BlockTarget.Gesture gesture;
        long gestureAt;
        boolean right = true, shown;
        /** The hand's point in model pixels, and the torso turn asked for {pitch, yaw, roll}. */
        final Vector3f grip = new Vector3f();
        final Vector3f supportGrip = new Vector3f();
        boolean support;
        final float[] lean = new float[3];
        /** How far into the reaching pose, 0..1, as shown. */
        float stretch;
        /** Smoothed visual extension; the entity pose and crouching flag never change. */
        float standUp;
        boolean crouching, overhead;
        long tracedAt;
        long contactAt;
        final Quaternionf contactTurn = new Quaternionf();
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Use blocks by hand", true,
                "On", "Looking at a block the hand can use - a chiseled bookshelf's slot - the hand goes to it, and puts in or takes out.",
                "Off", "Leave the arms to EMF.");
    }

    @Override
    public String id() {
        return "BlockUse";
    }

    @Override
    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        long now = context.now();
        State state = STATES.seen(player.getUUID(), now).value;
        state.lean[0] = state.lean[1] = state.lean[2] = 0f;
        boolean shown = false;
        float standUp = 0f;
        state.crouching = player.getPose() == Pose.CROUCHING;
        float stretchTarget = 0f;
        try {
            // On a seat the hands use what is in front of them as standing; the body stays seated.
            boolean seated = Seated.seated(player);
            if (!Seated.steady(player) || player.isSleeping() || player.isInWaterOrBubble()
                    || (player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING)) {
                state.pos = null;
                state.gesture = null;
                context.decide("off:state");
                return;
            }
            // The block held changed the way a hand changes it: the gesture.
            if (state.pos != null) {
                BlockState block = player.level().getBlockState(state.pos);
                Object seen = state.target.matches(block) ? state.target.snapshot(player, player.level(), state.pos, block) : block;
                if (!seen.equals(state.last)) {
                    BlockTarget.Gesture gesture = state.target.changed(state.pos, state.last, seen);
                    if (gesture != null) {
                        state.gesture = gesture;
                        state.gestureAt = now;
                    }
                    state.last = seen;
                }
            }
            double t = state.gesture == null ? 1 : (now - state.gestureAt) / 1e9 / GESTURE_SECONDS;
            if (t >= 1) state.gesture = null;

            BlockTarget.Spot spot;
            float outwards;
            // Driving with a train's controls: the hands are on its levers, looked at or not.
            TrainControls.Grips held = TrainControls.held(player);
            if (held != null) {
                state.gesture = null;
                state.pos = null;
                state.target = TrainControls.TARGET;
                spot = held.main();
                outwards = 0f;
            } else if (state.gesture != null) {
                spot = state.gesture.spot();
                float s = (float) Math.sin(Math.PI * t);
                switch (state.gesture.motion()) {
                    case PUT -> outwards = HOVER_OUT - PUT_IN * s;
                    case TAKE -> {
                        float e = (float) (1 - (1 - t) * (1 - t));
                        outwards = TAKE_FROM + (TAKE_TO - TAKE_FROM) * e;
                    }
                    default -> outwards = HOVER_OUT - TAP_IN * s;
                }
            } else {
                boolean still = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) <= SLOW_BELOW;
                spot = still ? look(player, state) : null;
                outwards = HOVER_OUT;
            }
            if (spot == null) {
                context.decide("none");
                return;
            }
            // Targets work in their block's own space; on a craft (a Sable sub-level) that is a
            // plot far off, drawn moved and turned: the hand goes to where it is drawn.
            SubLevels.Space space = state.pos == null ? SubLevels.WORLD : SubLevels.at(player.level(), state.pos);
            spot = inWorld(space, spot);

            IKFrame frame = context.frame();
            Vec3 point = spot.point().add(spot.out().scale(outwards / 16.0));
            Vector3f model = frame.relativeToJoint(point, new Vector3f());
            // The hand that holds what is used.
            boolean right = player.getMainArm() == HumanoidArm.RIGHT;
            Vector3f shoulder = right ? RIGHT_SHOULDER : LEFT_SHOULDER;
            IKResult ik = OneBoneIK.solveXY(frame, shoulder, point, ARM, 0f, 0f);
            if (ik == null || ik.reach() > MAX_REACH) {
                context.decide("out-of-reach");
                return;
            }
            float[] aim = {ik.x(), ik.y()};
            // Crouching and the arm not long enough for something above the shoulder, the player
            // stands up to it - and stays up while the hand is on it: stood up, it is in reach, and
            // would crouch again. Below the shoulder standing only takes the hand further off.
            Vec3 centre = state.target == null || state.pos == null ? null
                    : state.target.swayCentre(player.level(), state.pos, player.level().getBlockState(state.pos));
            if (centre != null) centre = space.toWorld(centre);
            Vector3f postureTarget = centre == null ? model : frame.relativeToJoint(centre.add(0, 7 / 16.0, 0), new Vector3f());
            state.overhead = postureTarget.y < shoulder.y;
            if (state.target != null && state.target.reachPose() && state.crouching && !seated) {
                standUp = ReachEnvelope.upright(postureTarget.x - shoulder.x,
                        postureTarget.y - shoulder.y, postureTarget.z - shoulder.z, ARM);
            }
            Effector effector = right ? Effector.RIGHT_ARM : Effector.LEFT_ARM;
            boolean fresh = !state.shown || right != state.right || InteractionRuntime.weight(player.getUUID(), effector, id()) < 1e-3f;
            if (centre != null || fresh) {
                state.grip.set(model);
            } else {
                state.grip.lerp(model, Smoothing.follow(context.dt(), held != null ? HELD_GRIP_SECONDS : GRIP_SECONDS));
            }
            state.right = right;
            if (centre != null) {
                // Off the middle: further (-z) leans forwards (+xRot), lower (+y) a little too; to
                // the right (-x) turns right (+yRot).
                Vector3f off = new Vector3f(model).sub(frame.relativeToJoint(centre, new Vector3f()));
                float k = 1f / SWAY_RADIUS;
                state.lean[0] = Mth.clamp((-off.z + 0.5f * off.y) * k, -1f, 1f) * SWAY_PITCH;
                state.lean[1] = Mth.clamp(-off.x * k, -1f, 1f) * SWAY_YAW;
            }
            if (state.target != null && state.target.balancesReach() && !seated && EMFCompatConfig.getBoolean(ButtonPress.KEY_STRETCH, true)) {
                // Past the arm's length the whole body reaches, as for a lever.
                Vector3f reachTarget = centre == null ? model : frame.relativeToJoint(centre, new Vector3f());
                stretchTarget = ReachPose.weight(new Vector3f(reachTarget).sub(shoulder).length() / ARM) * (1f - standUp);
                ReachPose.lean(reachTarget, stretchTarget, state.lean);
                // The pack already folds the crouching torso: do not add another full floor reach.
                if (state.crouching) state.lean[0] = Math.min(state.lean[0], (float) Math.toRadians(20));
            }
            BlockTarget.Spot support = held != null ? held.support()
                    : state.target.supportHand(player, state.pos, player.level().getBlockState(state.pos));
            if (support != null && held == null) support = inWorld(space, support);
            boolean supported = state.support;
            state.support = support != null;
            if (support != null) {
                Vec3 otherPoint = support.point().add(support.out().scale((held != null ? 0f : HOVER_OUT) / 16.0));
                IKResult other = OneBoneIK.solveXY(frame, right ? LEFT_SHOULDER : RIGHT_SHOULDER, otherPoint, ARM, 0f, 0f);
                if (other == null || other.reach() > MAX_REACH) {
                    context.decide("support-out-of-reach");
                    return;
                }
                Vector3f otherModel = frame.relativeToJoint(otherPoint, new Vector3f());
                // As the main hand: straight onto a grip that goes round (a wheel), followed otherwise.
                if (centre != null || fresh || !supported) state.supportGrip.set(otherModel);
                else state.supportGrip.lerp(otherModel, Smoothing.follow(context.dt(), held != null ? HELD_GRIP_SECONDS : GRIP_SECONDS));
                if (state.target instanceof SteeringWheel) {
                    state.lean[2] = WheelGeometry.steeringRoll(
                            right ? state.grip.y : state.supportGrip.y,
                            right ? state.supportGrip.y : state.grip.y);
                }
                Map<Effector, float[]> hands = new EnumMap<>(Effector.class);
                hands.put(effector, aim);
                hands.put(right ? Effector.LEFT_ARM : Effector.RIGHT_ARM, new float[]{other.x(), other.y()});
                out.add(Candidate.of(id(), Category.USE, priority(state), 1f, TIMING, hands));
            } else {
                out.add(Candidate.single(id(), Category.USE, priority(state), 1f, TIMING, effector, aim));
            }
            shown = true;
            // The click swings the arm; the gesture is the swing.
            context.claimArms();
            context.decide((state.gesture == null ? "hover" : state.gesture.motion().name().toLowerCase())
                    + (right ? "-R" : "-L"));
        } finally {
            state.shown = shown;
            state.standUp = ReachEnvelope.follow(state.standUp, standUp, context.dt());
            double dt = context.dt();
            state.stretch += (stretchTarget - state.stretch)
                    * (stretchTarget > state.stretch ? Smoothing.fadeIn(dt, ReachPose.SECONDS) : Smoothing.fadeOut(dt, ReachPose.SECONDS));
            if (state.stretch < 1e-3f) state.stretch = 0f;
        }
    }

    private static int priority(State state) {
        return state.target != null && state.target.quietsSwing() ? HELD_PRIORITY : PRIORITY;
    }

    /** A spot of {@code space}, in the world. */
    private static BlockTarget.Spot inWorld(SubLevels.Space space, BlockTarget.Spot spot) {
        return space.isWorld() ? spot : new BlockTarget.Spot(space.toWorld(spot.point()), space.directionToWorld(spot.out()));
    }

    /** The spot under the look on a block a hand uses, keeping the block to watch it change; {@code null} when none. */
    private static BlockTarget.Spot look(AbstractClientPlayer player, State state) {
        if (state.pos != null && state.target != null && state.target.quietsSwing()
                && player == Minecraft.getInstance().player && Minecraft.getInstance().options.keyUse.isDown()) {
            BlockState kept = player.level().getBlockState(state.pos);
            if (state.target.matches(kept)) {
                Vec3 local = state.target.swayCentre(player.level(), state.pos, kept);
                Vec3 centre = local == null ? null : SubLevels.toWorld(player.level(), state.pos, local);
                if (centre != null && centre.distanceTo(player.getEyePosition()) <= RANGE
                        && centre.subtract(player.getEyePosition()).normalize().dot(player.getViewVector(1f)) > 0.9
                        && Visibility.visible(player, state.pos, centre)) {
                    return state.target.hover(player, state.pos, kept,
                            new BlockHitResult(local, net.minecraft.core.Direction.UP, state.pos, false));
                }
            }
        }
        // At work on the block the hands are on: they stay, wherever the look goes.
        if (state.pos != null && state.target != null) {
            BlockState kept = player.level().getBlockState(state.pos);
            if (state.target.matches(kept) && state.target.holds(player, player.level(), state.pos, kept)) {
                return state.target.hover(player, state.pos, kept,
                        new BlockHitResult(Vec3.atCenterOf(state.pos), net.minecraft.core.Direction.UP, state.pos, false));
            }
        }
        // Our own player: the game's crosshair target, the block a click uses. Sable's sub-levels
        // (Aeronautics' craft) are in it, but not in a plain pick; others' is the pick.
        HitResult hit = player == Minecraft.getInstance().player && Minecraft.getInstance().hitResult != null
                ? Minecraft.getInstance().hitResult : player.pick(RANGE, 1f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            state.pos = null;
            return null;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState block = player.level().getBlockState(pos);
        // The first target of the block's with somewhere for the hand to go; with none, the first
        // of them is still watched - a use may change the block where the hand was not waiting.
        BlockTarget target = null;
        BlockTarget.Spot spot = null;
        for (BlockTarget each : TARGETS) {
            if (!each.matches(block)) continue;
            BlockTarget.Spot at = each.hover(player, pos, block, blockHit);
            if (at != null) {
                target = each;
                spot = at;
                break;
            }
            if (target == null) target = each;
        }
        if (target == null) {
            state.pos = null;
            return null;
        }
        if (!pos.equals(state.pos) || target != state.target) {
            state.gesture = null;
            state.pos = pos.immutable();
            state.last = target.snapshot(player, player.level(), pos, block);
        }
        state.target = target;
        return spot;
    }

    /** The limbs balancing the reaching pose. Called after the pack has animated, before the torso. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !INSTANCE.isEnabled()) return;
        if (!EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
        float w = InteractionRuntime.weight(uuid, state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, INSTANCE.id());
        ReachPose.upright(parts, state.standUp * w);
        boolean free = InteractionRuntime.weight(uuid, state.right ? Effector.LEFT_ARM : Effector.RIGHT_ARM) < 0.01f;
        ReachPose.balance(parts, state.right, state.stretch * w, free);
    }

    /**
     * Whether the swing is kept off this player's body now: the hand is on a target that asks for
     * it ({@link BlockTarget#quietsSwing}), most of the way.
     */
    public static boolean quietsSwing(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.shown || state.target == null || !state.target.quietsSwing()) return false;
        return InteractionRuntime.weight(uuid, state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, INSTANCE.id()) > 0.5f;
    }

    /** Close the overhead grip gap after all torso layers, before the final arm aim. */
    public static void reachContact(UUID uuid, Function<String, ModelPart> parts) {
        if (!EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
        State state = STATES.fresh(uuid);
        if (state == null) return;
        long now = System.nanoTime();
        double dt = state.contactAt == 0 ? 0 : (now - state.contactAt) * 1e-9;
        state.contactAt = now;
        boolean enabled = INSTANCE.isEnabled() && state.target != null
                && state.target.quietsSwing() && state.overhead
                && InteractionRuntime.weight(uuid, state.right ? Effector.LEFT_ARM : Effector.RIGHT_ARM) <= 0.01f;
        float w = enabled ? InteractionRuntime.weight(uuid,
                state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, INSTANCE.id()) : 0f;
        ReachPose.contact(parts, state.right, state.grip, w, state.contactTurn, dt);
    }

    /** The torso turn this asks for, {pitch, yaw, roll}; {@code null} when none. */
    public static float[] torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || state.lean[0] == 0f && state.lean[1] == 0f && state.lean[2] == 0f) return null;
        if (!INSTANCE.isEnabled()) return null;
        float w = InteractionRuntime.weight(uuid, state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, INSTANCE.id());
        return new float[]{state.lean[0] * w, state.lean[1] * w, state.lean[2] * w};
    }

    /**
     * Points the hand at its spot from where its shoulder is drawn this frame. Called last, after
     * the interaction runtime.
     */
    public static void aimArm(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !EMFCompatCore.isCompatEnabled()) return;
        Effector effector = state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM;
        float w = InteractionRuntime.weight(uuid, effector, INSTANCE.id());
        if (w < 1e-3f) return;
        ModelPart arm = parts.apply(effector.part);
        if (arm == null) return;
        Vector3f to = new Vector3f(state.grip).sub(arm.x, arm.y, arm.z);
        if (to.lengthSquared() < 1e-6f) return;
        to.normalize();
        // As OneBoneIK: the arm hangs along +y.
        float x = -(float) Math.acos(Mth.clamp(to.y, -1f, 1f));
        float y = (float) Math.atan2(-to.x, -to.z);
        arm.xRot += IKMath.wrap(x - arm.xRot) * w;
        arm.yRot += IKMath.wrap(y - arm.yRot) * w;
        arm.zRot *= 1f - w;
        if (state.support) {
            Effector other = state.right ? Effector.LEFT_ARM : Effector.RIGHT_ARM;
            float otherWeight = InteractionRuntime.weight(uuid, other, INSTANCE.id());
            ModelPart otherArm = parts.apply(other.part);
            if (otherArm != null && otherWeight > 1e-3f) {
                Vector3f direction = new Vector3f(state.supportGrip).sub(otherArm.x, otherArm.y, otherArm.z);
                if (direction.lengthSquared() > 1e-6f) {
                    direction.normalize();
                    float pitch = -(float) Math.acos(Mth.clamp(direction.y, -1f, 1f));
                    float yaw = (float) Math.atan2(-direction.x, -direction.z);
                    otherArm.xRot += IKMath.wrap(pitch - otherArm.xRot) * otherWeight;
                    otherArm.yRot += IKMath.wrap(yaw - otherArm.yRot) * otherWeight;
                    otherArm.zRot *= 1f - otherWeight;
                }
            }
        }
        long now = System.nanoTime();
        if (strm.emfcompat.animationadditions.footgrounding.FootGroundingFeature.isTrace()
                && now - state.tracedAt > 100_000_000L) {
            state.tracedAt = now;
            org.slf4j.LoggerFactory.getLogger("EMFCompatBlockUse").info(
                    "[UseTrace] target={} crouch={} upright={} weight={} stretch={} distancePx={} supportDistancePx={}",
                    state.target == null ? "none" : state.target.getClass().getSimpleName(),
                    state.crouching, state.standUp, w, state.stretch,
                    new Vector3f(state.grip).sub(arm.x, arm.y, arm.z).length(),
                    supportDistance(state, parts));
        }
    }

    private static float supportDistance(State state, Function<String, ModelPart> parts) {
        if (!state.support) return Float.NaN;
        ModelPart arm = parts.apply(state.right ? "left_arm" : "right_arm");
        return arm == null ? Float.NaN : new Vector3f(state.supportGrip).sub(arm.x, arm.y, arm.z).length();
    }
}
