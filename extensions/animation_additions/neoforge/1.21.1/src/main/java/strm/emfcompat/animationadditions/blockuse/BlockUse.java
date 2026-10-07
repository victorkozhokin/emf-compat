package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.world.level.block.Block;
import java.util.IdentityHashMap;
import strm.emfcompat.animationadditions.torso.TorsoLean;
import strm.emfcompat.animationadditions.DebugLog;
import strm.emfcompat.animationadditions.interaction.ArmAim;
import static strm.emfcompat.animationadditions.interaction.Skeleton.LEFT_SHOULDER;
import static strm.emfcompat.animationadditions.interaction.Skeleton.RIGHT_SHOULDER;
import strm.emfcompat.animationadditions.interaction.Skeleton;
import org.slf4j.LoggerFactory;
import net.minecraft.core.Direction;
import strm.emfcompat.animationadditions.torso.LowReach;
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
import strm.emfcompat.core.ik.IKResult;
import strm.emfcompat.core.ik.OneBoneIK;

import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import strm.emfcompat.animationadditions.interaction.Ease;
import strm.emfcompat.animationadditions.interaction.Body;
import strm.emfcompat.animationadditions.net.Inputs;
import strm.emfcompat.animationadditions.blockuse.aeronautics.*;
import strm.emfcompat.animationadditions.blockuse.create.*;
import strm.emfcompat.animationadditions.blockuse.supplementaries.*;
import strm.emfcompat.animationadditions.blockuse.vanilla.*;

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

    private static final String SUPPLEMENTARIES = "net.mehvahdjukaar.supplementaries.common.block.blocks.";

    private static final List<BlockTarget> TARGETS = List.of(new ChiseledShelf(), new Jukebox(), new Campfire(), new Vault(), new HandCrank(),
            new Composter(), new FlowerPot(), new RespawnAnchor(), new NoteBlock(), new Repeater(), new Comparator(),
            new DaylightDetector(), new Cake(), new Barrel(), new Candle(), new ValveHandle(), new SteeringWheel(),
            new CraftingTable(), new Stonecutter(), new Bell(), new FenceGate(), new Cauldron(), new Beehive(),
            new CandleCake(), new Tnt(), new Crafter(), new EnchantingTable(), new CartographyTable(),
            new ItemRest(ModBlock.exact("com.simibubi.create.content.logistics.depot.DepotBlock", "create:depot"), "getHeldItem", 13),
            new ItemDrain(), new Basin(), new BlazeBurner(), new ContraptionControls(),
            ItemRest.front(ModBlock.exact("com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock", "create:mechanical_crafter"), "getInventory"),
            ItemRest.front(ModBlock.exact("com.simibubi.create.content.kinetics.deployer.DeployerBlock", "create:deployer"), "heldItem"),
            ItemRest.front(ModBlock.exact("com.simibubi.create.content.logistics.packager.PackagerBlock", "create:packager"), "heldBox"),
            new ItemRest(ModBlock.exact("com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlock", "create:package_frogport"), "inventory", 12),
            ItemRest.table(ModBlock.exact("dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlock", "simulated:navigation_table"), "getHeldItem"),
            ItemRest.front(ModBlock.exact("dev.eriksonn.aeronautics.content.blocks.mounted_potato_cannon.MountedPotatoCannonBlock", "aeronautics:mounted_potato_cannon"), "getInventory"),
            new Typewriter(),
            new Bellows(), new SuppCrank(), new BookPile(), new Globe(), new Blackboard(), new SconceLever(), new Safe(), new FlowerBox(),
            ItemRest.inside(ModBlock.exact(SUPPLEMENTARIES + "ItemShelfBlock", "supplementaries:item_shelf"), "", 5),
            new ItemRest(ModBlock.exact(SUPPLEMENTARIES + "PedestalBlock", "supplementaries:pedestal"), "", 17),
            new ItemRest(ModBlock.exact(SUPPLEMENTARIES + "JarBlock", "supplementaries:jar"), "", 14),
            ItemRest.upright(ModBlock.exact(SUPPLEMENTARIES + "HourGlassBlock", "supplementaries:hourglass"), "", 16, 12),
            ItemRest.front(ModBlock.exact(SUPPLEMENTARIES + "NoticeBoardBlock", "supplementaries:notice_board"), ""),
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

    private static final float ARM = Skeleton.ARM_TO_FINGERTIPS;
    /** Looked for this far along the look, blocks. */
    private static final double RANGE = 3.0;
    /** Past the arm's length, as a share of it: further and the hand does not go. */
    private static final float MAX_REACH = 2.5f;
    /** Extra half-block of arm-space allowance for the high part of a grounded crank orbit. */
    private static final float GROUNDED_CRANK_REACH = 3.25f;
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
    /** A train's driver: this much forward to the levers, and no further round to them than this, radians. */
    private static final float DRIVER_PITCH = (float) Math.toRadians(8), DRIVER_YAW = (float) Math.toRadians(25);


    private BlockUse() {
    }

    private static final class State {
        /** The block looked at, as it was last seen, and its target. */
        BlockPos pos;
        Object last;
        /** For another player at the block's screen: how many slot actions their game had counted; -1 unknown. */
        int menuActions = -1;
        /** When this player last gave a sign of using something: a swing begun, the use key, a screen up. */
        long actedAt;
        boolean swinging;
        int swing;
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
        boolean crouching, overhead, groundReach;
        final CrankStance.State stance = new CrankStance.State();
        BlockTarget stanceTarget;
        BlockPos stancePos;
        final LowReach.State lowReach = new LowReach.State();
        long tracedAt;
        final TableSupport.State table = new TableSupport.State();
        final TableSupport.State bellows = new TableSupport.State();
        BlockTarget tableTarget;
        long tableUntil;
        boolean tableUnloading;
        long contactAt;
        final Quaternionf contactTurn = new Quaternionf();
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Use blocks by hand", true,
                "On", "Looking at a block the hand can use - shelves, workstations, Create and Supplementaries blocks, cockpit controls - the hand goes to it, and puts in, takes out, taps or holds on.",
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
        state.groundReach = false;
        state.tableUnloading = false;
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
            boolean swung = player.swinging && (!state.swinging || player.swingTime < state.swing);
            state.swinging = player.swinging;
            state.swing = player.swingTime;
            Minecraft mc = Minecraft.getInstance();
            if (swung || Inputs.useHeld(player) || Boolean.TRUE.equals(Inputs.menu(player)) || player == mc.player && mc.screen != null) state.actedAt = now;
            // The block held changed the way a hand changes it: the gesture.
            if (state.pos != null) {
                BlockState block = player.level().getBlockState(state.pos);
                Object seen = state.target.matches(block) ? state.target.snapshot(player, player.level(), state.pos, block) : block;
                // What changes in a block's own screen is the doing of whoever has it up, not of everyone looking on.
                Boolean menu = state.target.menu() ? Inputs.menu(player) : null;
                BlockPos menuPos = menu == null ? null : Inputs.menuPos(player);
                boolean theirs = (menu == null || menu && (menuPos == null || menuPos.equals(state.pos))) && !othersDoing(player, state);
                if (!seen.equals(state.last)) {
                    BlockTarget.Gesture gesture = theirs ? state.target.changed(state.pos, state.last, seen) : null;
                    if (gesture != null) {
                        state.gesture = gesture;
                        state.gestureAt = now;
                    }
                    state.last = seen;
                }
                // Another player's screen shows nothing here but that something was moved in it: the hand goes down once.
                int moved = menu != null && menu && theirs && player != Minecraft.getInstance().player ? Inputs.menuActions(player) : -1;
                if (moved >= 0 && state.menuActions >= 0 && moved != state.menuActions && state.gesture == null && state.target.matches(block)) {
                    BlockTarget.Spot at = state.target.hover(player, state.pos, block,
                            new BlockHitResult(Vec3.atCenterOf(state.pos), net.minecraft.core.Direction.UP, state.pos, false));
                    if (at != null) {
                        state.gesture = new BlockTarget.Gesture(at, BlockTarget.Motion.PUT);
                        state.gestureAt = now;
                    }
                }
                state.menuActions = moved;
            } else {
                state.menuActions = -1;
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
                outwards = outwards(state.gesture.motion(), t);
            } else {
                boolean still = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) <= SLOW_BELOW;
                BlockPos oldPos = state.pos;
                BlockTarget oldTarget = state.target;
                spot = still ? look(player, state) : null;
                if (spot != null && state.target != null && state.target.supportSurface(player.level().getBlockState(state.pos)) != null)
                    state.tableUntil = now + 350_000_000L;
                if (spot == null && still && oldPos != null && oldTarget != null && now < state.tableUntil) {
                    BlockState oldBlock = player.level().getBlockState(oldPos);
                    if (oldTarget.matches(oldBlock) && oldTarget.supportSurface(oldBlock) != null
                            && SubLevels.toWorld(player.level(), oldPos, Vec3.atCenterOf(oldPos)).distanceTo(player.position()) < 2) {
                        state.pos = oldPos;
                        state.target = oldTarget;
                        state.tableUnloading = true;
                        spot = oldTarget.hover(player, oldPos, oldBlock, new BlockHitResult(Vec3.atCenterOf(oldPos), net.minecraft.core.Direction.UP, oldPos, false));
                    }
                }
                outwards = HOVER_OUT;
            }
            if (spot == null) {
                context.decide("none");
                return;
            }
            // Targets work in their block's own space; on a craft (a Sable sub-level) that is a
            // plot far off, drawn moved and turned: the hand goes to where it is drawn.
            SubLevels.Space space = state.pos == null ? SubLevels.WORLD : SubLevels.at(player.level(), state.pos);
            boolean table = state.target != null && state.pos != null && state.target.supportSurface(player.level().getBlockState(state.pos)) != null;
            if (table) state.tableTarget = state.target;
            if ((table || state.target instanceof Bellows) && state.gesture == null) outwards = .5f;
            spot = inWorld(space, spot);

            IKFrame frame = context.frame();
            Vec3 point = spot.point().add(spot.out().scale(outwards / 16.0));
            if (state.gesture != null && state.gesture.sweep() != null) {
                // Across the spot, from half the sweep before it to half past it, eased at both ends.
                double along = Ease.smooth(t) - 0.5;
                point = point.add(space.directionToWorld(state.gesture.sweep()).scale(along));
            }
            Vector3f model = Body.model(frame, point);
            // The hand that holds what is used.
            boolean right = player.getMainArm() == HumanoidArm.RIGHT;
            Vector3f shoulder = right ? RIGHT_SHOULDER : LEFT_SHOULDER;
            IKResult ik = OneBoneIK.solveXY(frame, shoulder, point, ARM, 0f, 0f);
            float reachLimit = state.target instanceof HandCrank && player.onGround() && !seated
                    ? GROUNDED_CRANK_REACH : MAX_REACH;
            Object contactIdentity = state.pos == null ? null : new strm.emfcompat.animationadditions.interaction.ContactTarget(
                    space, state.pos, player.level().getBlockState(state.pos).getBlock());
            boolean retained = InteractionRuntime.holds(player.getUUID(), id(), right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, contactIdentity);
            if (ik == null || !strm.emfcompat.animationadditions.interaction.ContactReach.accepts(ik.reach(), reachLimit, retained)) {
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
            Vector3f postureTarget = centre == null ? model : Body.model(frame, centre.add(0, 7 / 16.0, 0));
            state.overhead = postureTarget.y < shoulder.y;
            state.groundReach = player.onGround() && !seated
                    && state.target != null && state.target.reachPose()
                    && (!state.crouching || (wheel(state) && centre != null
                    ? Body.model(frame, centre).y : model.y) > shoulder.y + 4);
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
            if (centre != null) sway(state, new Vector3f(model).sub(Body.model(frame, centre)));
            if (state.target != null && state.target.balancesReach() && !seated && EMFCompatConfig.getBoolean(ButtonPress.KEY_STRETCH, true)) {
                // Past the arm's length the whole body reaches, as for a lever.
                Vector3f reachTarget = centre == null ? model : Body.model(frame, centre);
                stretchTarget = ReachPose.weight(new Vector3f(reachTarget).sub(shoulder).length() / ARM) * (1f - standUp);
                if (!state.groundReach) ReachPose.lean(reachTarget, stretchTarget, state.lean);
                // The pack already folds the crouching torso: do not add another full floor reach.
                if (state.crouching) state.lean[0] = Math.min(state.lean[0], (float) Math.toRadians(20));
            }
            BlockTarget.Spot support = held != null ? held.support()
 : state.target == null || state.pos == null ? null
                    : state.target.supportHand(player, state.pos, player.level().getBlockState(state.pos));
            if (support != null && held == null) support = inWorld(space, support);
            boolean supported = state.support;
            state.support = support != null;
            if (support != null) {
                Vec3 otherPoint = support.point().add(support.out().scale((held != null ? 0f : table || state.target instanceof Bellows ? .5f : HOVER_OUT) / 16.0));
                IKResult other = OneBoneIK.solveXY(frame, right ? LEFT_SHOULDER : RIGHT_SHOULDER, otherPoint, ARM, 0f, 0f);
                if (other == null || !strm.emfcompat.animationadditions.interaction.ContactReach.accepts(other.reach(), MAX_REACH, retained)) {
                    context.decide("support-out-of-reach");
                    return;
                }
                Vector3f otherModel = Body.model(frame, otherPoint);
                // As the main hand: straight onto a grip that goes round (a wheel), followed otherwise.
                if (centre != null || fresh || !supported) state.supportGrip.set(otherModel);
                else state.supportGrip.lerp(otherModel, Smoothing.follow(context.dt(), held != null ? HELD_GRIP_SECONDS : GRIP_SECONDS));
                if (state.target instanceof SteeringWheel) {
                    state.lean[2] = WheelGeometry.steeringRoll(
                            right ? state.grip.y : state.supportGrip.y,
                            right ? state.supportGrip.y : state.grip.y);
                }
                if (held != null) {
                    // A driver leans to the levers a little, and turns to them when they are off to one side.
                    Vector3f levers = new Vector3f(state.grip).add(state.supportGrip).mul(.5f);
                    state.lean[0] = DRIVER_PITCH;
                    state.lean[1] = Mth.clamp((float) Math.atan2(-levers.x, -levers.z) * .5f, -DRIVER_YAW, DRIVER_YAW);
                }
                Map<Effector, float[]> hands = new EnumMap<>(Effector.class);
                hands.put(effector, aim);
                hands.put(right ? Effector.LEFT_ARM : Effector.RIGHT_ARM, new float[]{other.x(), other.y()});
                out.add(Candidate.of(id(), Category.USE, priority(state), 1f, TIMING, hands));
            } else {
                out.add(Candidate.single(id(), Category.USE, priority(state), 1f, TIMING, effector, aim));
            }
            for (int i = out.size() - 1; i >= 0; i--) {
                Candidate c = out.get(i);
                if (!c.source().equals(id())) break;
                out.set(i, c.withTarget(contactIdentity).withQuietSwing(state.target != null && state.target.quietsSwing()));
            }
            shown = true;
            // The click swings the arm; the gesture is the swing.
            context.claimArms();
            context.decide((state.gesture == null ? "hover" : state.gesture.motion().name().toLowerCase())
                    + (right ? "-R" : "-L"));
        } finally {
            state.shown = shown;
            boolean wasWheel = state.stanceTarget instanceof ValveHandle || state.stanceTarget instanceof SteeringWheel;
            if ((wheel(state) || wasWheel) && (state.stanceTarget != state.target || !java.util.Objects.equals(state.stancePos, state.pos))) {
                state.stance.angle = null;
                state.stance.motionAt = 0;
                state.stance.direction = 0;
            }
            state.stanceTarget = state.target;
            state.stancePos = state.pos;
            CrankStance.observe(state.stance, player, context.frame(),
                    shown && state.target != null && state.pos != null
                            ? state.target.stanceAngle(player.level(), state.pos) : null);
            state.standUp = ReachEnvelope.follow(state.standUp, standUp, context.dt());
            double dt = context.dt();
            state.stretch += (stretchTarget - state.stretch)
                    * (stretchTarget > state.stretch ? Smoothing.fadeIn(dt, ReachPose.SECONDS) : Smoothing.fadeOut(dt, ReachPose.SECONDS));
            if (state.stretch < 1e-3f) state.stretch = 0f;
        }
    }

    /** How far out of the block the hand is, pixels, {@code t} 0..1 of the way through a gesture. */
    private static float outwards(BlockTarget.Motion motion, double t) {
        float s = (float) Math.sin(Math.PI * t);
        switch (motion) {
            case PUT:
                return HOVER_OUT - PUT_IN * s;
            case TAKE:
                float e = (float) (1 - (1 - t) * (1 - t));
                return TAKE_FROM + (TAKE_TO - TAKE_FROM) * e;
            case HOLD:
                return 0f;
            default:
                return HOVER_OUT - TAP_IN * s;
        }
    }

    /**
     * The torso going with a hand that goes round, {@code off} the middle of its round by this,
     * model pixels: further (-z) leans forwards (+xRot), lower (+y) a little too; to the right
     * (-x) turns right (+yRot).
     */
    private static void sway(State state, Vector3f off) {
        float k = 1f / SWAY_RADIUS;
        state.lean[0] = Mth.clamp((-off.z + 0.5f * off.y) * k, -1f, 1f) * SWAY_PITCH;
        state.lean[1] = Mth.clamp(-off.x * k, -1f, 1f) * SWAY_YAW;
    }

    private static boolean wheel(State state) {
        return state.target instanceof ValveHandle || state.target instanceof SteeringWheel;
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
                && Inputs.useHeld(player)) {
            BlockState kept = player.level().getBlockState(state.pos);
            if (state.target.matches(kept)) {
                Vec3 local = state.target.swayCentre(player.level(), state.pos, kept);
                Vec3 centre = local == null ? null : SubLevels.toWorld(player.level(), state.pos, local);
                if (centre != null && centre.distanceTo(player.getEyePosition()) <= RANGE
                        && centre.subtract(player.getEyePosition()).normalize().dot(player.getViewVector(1f)) > 0.9
                        && Visibility.visible(player, state.pos, centre)) {
                    return state.target.hover(player, state.pos, kept,
                            new BlockHitResult(local, Direction.UP, state.pos, false));
                }
            }
        }
        // At work on the block the hands are on: they stay, wherever the look goes.
        if (state.pos != null && state.target != null) {
            BlockState kept = player.level().getBlockState(state.pos);
            if (state.target.matches(kept) && state.target.holds(player, player.level(), state.pos, kept)) {
                return state.target.hover(player, state.pos, kept,
                        new BlockHitResult(Vec3.atCenterOf(state.pos), Direction.UP, state.pos, false));
            }
        }
        // Our own player: the game's crosshair target, the block a click uses. Sable's sub-levels
        // (Aeronautics' craft) are in it, but not in a plain pick; others' is the pick.
        HitResult hit = Inputs.sight(player, RANGE, 1f);
        // At a block's own screen the hands are on that block, wherever the look has gone since.
        BlockPos menuPos = Inputs.menuPos(player);
        if (menuPos != null && !(hit instanceof BlockHitResult looked && looked.getBlockPos().equals(menuPos))) {
            BlockTarget[] screened = targetsOf(player.level().getBlockState(menuPos));
            if (screened.length > 0 && screened[0].menu()
                    && SubLevels.toWorld(player.level(), menuPos, Vec3.atCenterOf(menuPos)).distanceTo(player.getEyePosition()) <= RANGE + 1)
                hit = new BlockHitResult(Vec3.atCenterOf(menuPos), Direction.UP, menuPos, false);
        }
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
        for (BlockTarget each : targetsOf(block)) {
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
            state.table.motion.load = state.table.motion.settled = 0;
            state.table.supportGap = Float.POSITIVE_INFINITY;
            state.table.turn.identity();
            state.gesture = null;
            state.pos = pos.immutable();
            state.last = target.snapshot(player, player.level(), pos, block);
        }
        state.target = target;
        return spot;
    }

    /** The control our own player's hands are kept on while the eyes wander - a wheel, a keyboard, a plate - or {@code null}. */
    public static BlockPos heldBlock(AbstractClientPlayer player) {
        State state = STATES.fresh(player.getUUID());
        if (state == null || state.pos == null || state.target == null) return null;
        BlockState kept = player.level().getBlockState(state.pos);
        return state.target.matches(kept) && state.target.holds(player, player.level(), state.pos, kept) ? state.pos : null;
    }

    /** How long a sign of using something stands for what changes next. */
    private static final long ACTED_NANOS = 1_500_000_000L;

    /**
     * Whether what just changed in the block was another player's doing: someone else has their
     * hands at the same block and gave a sign of using it more lately than this player did. With no
     * such sign from anyone it is everyone's who is at it, as nothing tells them apart.
     */
    private static boolean othersDoing(AbstractClientPlayer player, State state) {
        for (AbstractClientPlayer other : Minecraft.getInstance().level.players()) {
            if (other == player) continue;
            State theirs = STATES.fresh(other.getUUID());
            if (theirs != null && state.pos.equals(theirs.pos) && theirs.actedAt > state.actedAt
                    && System.nanoTime() - theirs.actedAt < ACTED_NANOS) return true;
        }
        return false;
    }

    /** The targets a block is one of, in {@link #TARGETS}' order; found once for each block. */
    private static BlockTarget[] targetsOf(BlockState block) {
        return MATCHING.computeIfAbsent(block.getBlock(), b -> TARGETS.stream().filter(t -> t.matches(block)).toArray(BlockTarget[]::new));
    }

    private static final Map<Block, BlockTarget[]> MATCHING = new IdentityHashMap<>();

    /** The limbs balancing the reaching pose. Called after the pack has animated, before the torso. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null) return;
        state.table.snapshot.clear();
        state.bellows.snapshot.clear();
        if (!INSTANCE.isEnabled()) return;
        if (!EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
        float w = InteractionRuntime.weight(uuid, state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, INSTANCE.id());
        ReachPose.upright(parts, state.standUp * w);
        boolean free = InteractionRuntime.weight(uuid, state.right ? Effector.LEFT_ARM : Effector.RIGHT_ARM) < 0.01f;
        ReachPose.balance(parts, state.right, state.stretch * w, free, !state.groundReach && !state.lowReach.active());
    }

    /**
     * Whether the swing is kept off this player's body now: the hand is on a target that asks for
     * it ({@link BlockTarget#quietsSwing}), most of the way.
     */
    public static boolean quietsSwing(UUID uuid) {
        return InteractionRuntime.quietsSwing(uuid);
    }

    /** Close the overhead grip gap after all torso layers, before the final arm aim. */
    public static void reachContact(UUID uuid, Function<String, ModelPart> parts) {
        if (!EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) return;
        State state = STATES.fresh(uuid);
        if (state == null) return;
        if (state.target != null && state.target == state.tableTarget && (state.pos == null || state.target.supportSurface(net.minecraft.client.Minecraft.getInstance().level.getBlockState(state.pos)) != null)) {
            float tableWeight = Math.min(InteractionRuntime.weight(uuid, Effector.RIGHT_ARM, INSTANCE.id()), InteractionRuntime.weight(uuid, Effector.LEFT_ARM, INSTANCE.id()));
            TableSupport.apply(state.table, parts, net.minecraft.client.Minecraft.getInstance().level.getPlayerByUUID(uuid) instanceof AbstractClientPlayer p ? p : null,
                    state.shown && !state.tableUnloading, state.right, state.grip, state.supportGrip, tableWeight);
        }
        if (state.target instanceof Bellows) {
            var player = net.minecraft.client.Minecraft.getInstance().level.getPlayerByUUID(uuid);
            if (player instanceof AbstractClientPlayer p) {
                float weight = Math.min(InteractionRuntime.weight(uuid, Effector.RIGHT_ARM, INSTANCE.id()), InteractionRuntime.weight(uuid, Effector.LEFT_ARM, INSTANCE.id()));
                float press = state.pos == null ? 0 : BellowsGeometry.compression(Bellows.height(p.level(), state.pos));
                TableSupport.apply(state.bellows, parts, p, state.shown && state.pos != null && Bellows.pressing(p, state.pos),
                        state.right, state.grip, state.supportGrip, weight, press);
                if (!state.bellows.snapshot.isEmpty()) state.bellows.snapshot.put("compression", press);
            }
        }
        boolean lowFree = InteractionRuntime.weight(uuid, state.right ? Effector.LEFT_ARM : Effector.RIGHT_ARM) <= 0.01f;
        float mainOwned = InteractionRuntime.weight(uuid, state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, INSTANCE.id());
        float otherOwned = InteractionRuntime.weight(uuid, state.right ? Effector.LEFT_ARM : Effector.RIGHT_ARM, INSTANCE.id());
        boolean both = wheel(state) && state.support;
        // A wheel owns both effectors together. Do not treat its supporting hand as a competing interaction.
        float lowWeight = INSTANCE.isEnabled() && state.groundReach && (lowFree || both)
                && EMFCompatConfig.getBoolean(ButtonPress.KEY_STRETCH, true)
                ? (both ? Math.min(mainOwned, otherOwned) : mainOwned) : 0;
        state.lowReach.weightShift = CrankStance.apply(state.stance, parts, lowWeight);
        if (both) LowReach.apply(parts, state.right, state.grip, state.supportGrip, lowWeight, state.lowReach);
        else LowReach.apply(parts, state.right, state.grip, lowWeight, state.lowReach);
        long now = System.nanoTime();
        double dt = state.contactAt == 0 ? 0 : (now - state.contactAt) * 1e-9;
        state.contactAt = now;
        boolean enabled = INSTANCE.isEnabled() && state.target != null
                && state.target.quietsSwing() && state.overhead && !state.groundReach
                && InteractionRuntime.weight(uuid, state.right ? Effector.LEFT_ARM : Effector.RIGHT_ARM) <= 0.01f;
        float w = enabled ? InteractionRuntime.weight(uuid,
                state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, INSTANCE.id()) : 0f;
        ReachPose.contact(parts, state.right, state.grip, w, state.contactTurn, dt);
    }

    /** The torso turn this asks for; {@code null} when none. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || state.lean[0] == 0f && state.lean[1] == 0f && state.lean[2] == 0f) return null;
        if (!INSTANCE.isEnabled()) return null;
        float w = InteractionRuntime.weight(uuid, state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, INSTANCE.id());
        return TorsoLean.Hint.turn(state.lean[0] * w, state.lean[1] * w, state.lean[2] * w);
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
        if (new Vector3f(state.grip).sub(arm.x, arm.y, arm.z).lengthSquared() < 1e-6f) return;
        ArmAim.towards(arm, state.grip, w, true);
        if (state.support) {
            Effector other = state.right ? Effector.LEFT_ARM : Effector.RIGHT_ARM;
            float otherWeight = InteractionRuntime.weight(uuid, other, INSTANCE.id());
            ModelPart otherArm = parts.apply(other.part);
            if (otherArm != null && otherWeight > 1e-3f) ArmAim.towards(otherArm, state.supportGrip, otherWeight, true);
        }
        TableSupport.capture(state.table, parts, state.right, state.grip, state.supportGrip);
        TableSupport.capture(state.bellows, parts, state.right, state.grip, state.supportGrip);
        long now = System.nanoTime();
        if (DebugLog.trace()
                && now - state.tracedAt > 100_000_000L) {
            state.tracedAt = now;
            LoggerFactory.getLogger("EMFCompatBlockUse").info(
                    "[UseTrace] target={} crouch={} upright={} weight={} stretch={} distancePx={} supportDistancePx={}",
                    state.target == null ? "none" : state.target.getClass().getSimpleName(),
                    state.crouching, state.standUp, w, state.stretch,
                    new Vector3f(state.grip).sub(arm.x, arm.y, arm.z).length(),
                    supportDistance(state, parts));
            if (wheel(state)) org.slf4j.LoggerFactory.getLogger("EMFCompatBlockUse").info(
                    "[WheelTrace] target={} grounded={} mainWeight={} supportWeight={} roll={} load={}",
                    state.target.getClass().getSimpleName(), state.groundReach, w,
                    InteractionRuntime.weight(uuid, state.right ? Effector.LEFT_ARM : Effector.RIGHT_ARM, INSTANCE.id()),
                    state.lean[2], state.stance.load);
        }
    }

    /** Moving bellows plate is sampled in the same frame as its native renderer. */
    public static void frame(AbstractClientPlayer player, IKFrame frame) {
        State s = STATES.fresh(player.getUUID());
        if (s == null || !s.shown || !(s.target instanceof Bellows) || s.pos == null) return;
        var block = player.level().getBlockState(s.pos);
        if (!s.target.matches(block)) return;
        var space = SubLevels.at(player.level(), s.pos);
        s.bellows.obstacleMin = new Vector3f(Float.POSITIVE_INFINITY);
        s.bellows.obstacleMax = new Vector3f(Float.NEGATIVE_INFINITY);
        for (int x = 0; x <= 1; x++) for (int y = 0; y <= 1; y++) for (int z = 0; z <= 1; z++) {
            var corner = Body.model(frame, space.toWorld(new Vec3(s.pos.getX() + x, s.pos.getY() + y, s.pos.getZ() + z)));
            s.bellows.obstacleMin.min(corner);
            s.bellows.obstacleMax.max(corner);
        }
        var main = Bellows.contact(player, s.pos, block, true);
        var other = Bellows.contact(player, s.pos, block, false);
        s.grip.set(Body.model(frame, space.toWorld(main.point().add(main.out().scale(.5 / 16)))));
        s.supportGrip.set(Body.model(frame, space.toWorld(other.point().add(other.out().scale(.5 / 16)))));
    }
    public static Map<String, Object> bellowsSnapshot(UUID uuid) {
        State s = STATES.fresh(uuid);
        return s == null ? Map.of() : new java.util.LinkedHashMap<>(s.bellows.snapshot);
    }

    /** Same-render table measurements for native regression checks. */
    public static Map<String, Object> tableSnapshot(UUID uuid) {
        State s = STATES.fresh(uuid);
        return s == null ? Map.of() : new java.util.LinkedHashMap<>(s.table.snapshot);
    }

    private static float supportDistance(State state, Function<String, ModelPart> parts) {
        if (!state.support) return Float.NaN;
        ModelPart arm = parts.apply(state.right ? "left_arm" : "right_arm");
        return arm == null ? Float.NaN : new Vector3f(state.supportGrip).sub(arm.x, arm.y, arm.z).length();
    }
}
