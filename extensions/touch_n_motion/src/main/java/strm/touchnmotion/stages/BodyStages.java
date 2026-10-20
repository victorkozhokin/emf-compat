package strm.touchnmotion.stages;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import strm.emfcompat.core.ik.IKFrame;
import strm.touchnmotion.blockuse.BlockUse;
import strm.touchnmotion.blockuse.aeronautics.CockpitControls;
import strm.touchnmotion.blockuse.create.TrainCockpit;
import strm.touchnmotion.buttonpress.ButtonPress;
import strm.touchnmotion.buttonpress.aeronautics.HeavyThrottle;
import strm.touchnmotion.compat.CombatBody;
import strm.touchnmotion.create.ejector.EjectorLaunch;
import strm.touchnmotion.fishing.Fishing;
import strm.touchnmotion.footgrounding.compat.FootGrounding;
import strm.touchnmotion.fright.Fright;
import strm.touchnmotion.gesture.Gesture;
import strm.touchnmotion.interaction.Effector;
import strm.touchnmotion.interaction.HandContacts;
import strm.touchnmotion.interaction.InteractionProvider;
import strm.touchnmotion.interaction.InteractionRuntime;
import strm.touchnmotion.lead.LeadHold;
import strm.touchnmotion.lookat.LookAt;
import strm.touchnmotion.mining.Mining;
import strm.touchnmotion.motion.MotionRuntime;
import strm.touchnmotion.pocket.PocketStash;
import strm.touchnmotion.ride.BoatPassenger;
import strm.touchnmotion.ride.BoatRide;
import strm.touchnmotion.ride.HorseRide;
import strm.touchnmotion.ride.MinecartRide;
import strm.touchnmotion.torso.TorsoLean;
import strm.touchnmotion.transport.TransportGrip;
import strm.touchnmotion.wallhand.FenceLean;
import strm.touchnmotion.wallhand.WallSqueeze;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Who works on the body, and in what order: the one place a feature is put into the draw.
 *
 * <p>A draw goes through the same stages every time. Before the pack animates, the model is turned
 * ({@link #ORIENT}) and its space read ({@link #FRAME}). After it: the legs and the seat
 * ({@link #LEGS}), what the body leans and stands on ({@link #SUPPORT}), the torso - which sums the
 * {@link #TORSO} hints -, the reach from where the shoulders now are ({@link #REACH}), and last the
 * hands and the head ({@link #HANDS}). What the runtime does between them - opening and closing the
 * support and hands passes, the arbiter's own blend - stays in the hook, around the stages.</p>
 *
 * <p>Within a stage the order is the order of the list, and it matters: a later step works from
 * what the earlier ones left. A new feature adds its steps here, where they go.</p>
 */
public final class BodyStages {

    /** What a step is told about the draw besides the parts: the arms as the pack had them, and whether this is the main model. */
    public record Pass(Map<Effector, float[]> base, boolean mainModel) {
    }

    @FunctionalInterface
    public interface Step {
        void apply(UUID uuid, Function<String, ModelPart> parts, Pass pass);
    }

    /** Every provider of interactions. The order is only the log's; who wins is the arbiter's call. */
    public static final List<InteractionProvider> PROVIDERS = List.of(
            strm.touchnmotion.wallhand.WallHand.INSTANCE,
            FenceLean.INSTANCE,
            Fishing.INSTANCE,
            Fright.INSTANCE,
            strm.touchnmotion.plantreach.PlantReach.INSTANCE,
            LookAt.INSTANCE,
            LeadHold.INSTANCE,
            PocketStash.INSTANCE,
            strm.touchnmotion.gesture.AnimalCare.INSTANCE,
            strm.touchnmotion.gesture.HandTo.INSTANCE,
            strm.touchnmotion.gesture.ShakeOff.INSTANCE,
            strm.touchnmotion.gesture.ArmorDon.INSTANCE,
            strm.touchnmotion.gesture.ContainerSearch.INSTANCE,
            TransportGrip.INSTANCE,
            ButtonPress.INSTANCE,
            HeavyThrottle.INSTANCE,
            BlockUse.INSTANCE,
            CockpitControls.INSTANCE,
            strm.touchnmotion.doorhold.DoorHold.INSTANCE,
            strm.touchnmotion.furniture.Furniture.INSTANCE,
            Mining.INSTANCE,
            BoatRide.INSTANCE,
            BoatPassenger.INSTANCE,
            MinecartRide.INSTANCE,
            HorseRide.INSTANCE);

    /** At the end of each client tick: what a feature keeps of the game's own state, as a boat keeps its rider's body. */
    public static final List<Runnable> TICK = List.of(
            strm.touchnmotion.compat.GripMarkers::tick,
            TrainCockpit::tick);

    /** A hand on a control the camera is left free at: a throttle, a typewriter, a wheel, a train's levers. */
    public static final List<Predicate<AbstractClientPlayer>> AT_CONTROLS = List.of(
            player -> strm.touchnmotion.buttonpress.aeronautics.ThrottleLever.heldPosition() != null,
            player -> strm.touchnmotion.blockuse.aeronautics.Typewriter.activePosition(player) != null,
            player -> strm.touchnmotion.blockuse.aeronautics.SteeringWheel.held(null),
            player -> strm.touchnmotion.blockuse.create.TrainControls.held(player) != null);

    /** A seat that turns the whole body itself, torso and facing: the torso's own lean stays out of it. */
    public static final List<Predicate<UUID>> SEAT_BODY = List.of(
            CockpitControls::active);

    /**
     * What moves the whole rider before they are drawn - carried with the saddle, tilted with a horse on uneven
     * ground - the innermost last. Each says whether it pushed a pose on the stack; they are taken off after the
     * draw in the opposite order.
     */
    public static final List<RiderDraw> RIDER = List.of(
            strm.touchnmotion.horsesync.ClientEventHandler::carryRider,
            strm.touchnmotion.footgrounding.FootGroundingFeature::tiltRider);

    @FunctionalInterface
    public interface RiderDraw {
        boolean before(net.minecraft.world.entity.player.Player player, PoseStack stack, float partialTick);
    }

    /** The model let down onto the ground before its space is read: the feet's own lowering. */
    public static final List<BiConsumer<AbstractClientPlayer, PoseStack>> MODEL = List.of(
            FootGrounding::modelPose);

    /** The model turned before it is drawn - to a wheel, a cart, a fence, someone looked at. */
    public static final List<BiConsumer<AbstractClientPlayer, PoseStack>> ORIENT = List.of(
            CockpitControls::orient,
            TrainCockpit::orient,
            MinecartRide::orient,
            FenceLean::orient,
            LookAt::orient);

    /** The model's space read, once the feet have set it, before the pack animates. */
    public static final List<BiConsumer<AbstractClientPlayer, IKFrame>> FRAME = List.of(
            CockpitControls::frame,
            (player, frame) -> MotionRuntime.modelPose(player),
            InteractionRuntime::modelPose,
            BlockUse::frame,
            (player, frame) -> EjectorLaunch.modelPose(player),
            WallSqueeze::modelPose,
            (player, frame) -> TorsoLean.modelPose(player));

    /** The legs and the seat: before anything that works from where they are. */
    public static final List<Step> LEGS = List.of(
            (uuid, parts, pass) -> FootGrounding.apply(uuid, parts),
            (uuid, parts, pass) -> MinecartRide.legs(uuid, parts),
            (uuid, parts, pass) -> HorseRide.legs(uuid, parts),
            (uuid, parts, pass) -> BoatRide.seat(uuid, parts),
            (uuid, parts, pass) -> BoatPassenger.legs(uuid, parts),
            (uuid, parts, pass) -> EjectorLaunch.apply(uuid, parts),
            (uuid, parts, pass) -> WallSqueeze.apply(uuid, parts));

    /** What the body stands and leans on: the feet's steps, the pelvis, the counterweight. */
    public static final List<Step> SUPPORT = List.of(
            (uuid, parts, pass) -> HandContacts.support(uuid, parts),
            (uuid, parts, pass) -> ButtonPress.apply(uuid, parts),
            (uuid, parts, pass) -> BlockUse.apply(uuid, parts),
            (uuid, parts, pass) -> LeadHold.support(uuid, parts),
            (uuid, parts, pass) -> PocketStash.support(uuid, parts),
            (uuid, parts, pass) -> FenceLean.support(uuid, parts),
            (uuid, parts, pass) -> Fishing.support(uuid, parts),
            (uuid, parts, pass) -> LookAt.support(uuid, parts),
            (uuid, parts, pass) -> Fright.support(uuid, parts),
            (uuid, parts, pass) -> Mining.support(uuid, parts),
            (uuid, parts, pass) -> Gesture.support(uuid, parts),
            (uuid, parts, pass) -> CombatBody.support(uuid, parts),
            // The torso before the arm aims: a hand on a wall aims from where the shoulder has gone.
            (uuid, parts, pass) -> WallSqueeze.support(uuid, parts),
            (uuid, parts, pass) -> TransportGrip.support(uuid, parts));

    /** What each feature asks of the torso this frame; summed. */
    public static final List<Function<UUID, TorsoLean.Hint>> TORSO = List.of(
            CombatBody::torsoHint,
            HandContacts::torsoHint,
            ButtonPress::torsoHint,
            uuid -> TorsoLean.Hint.of(HeavyThrottle.torsoHint(uuid)),
            BlockUse::torsoHint,
            TrainCockpit::torsoHint,
            EjectorLaunch::torsoHint,
            WallSqueeze::torsoHint,
            uuid -> TorsoLean.Hint.of(TransportGrip.torsoHint(uuid)),
            uuid -> TorsoLean.Hint.of(FootGrounding.terrainHint(uuid)),
            uuid -> TorsoLean.Hint.of(LeadHold.torsoHint(uuid)),
            PocketStash::torsoHint,
            Mining::torsoHint,
            Gesture::torsoHint,
            BoatRide::torsoHint,
            BoatPassenger::torsoHint,
            MinecartRide::torsoHint,
            HorseRide::torsoHint,
            LookAt::torsoHint,
            FenceLean::torsoHint,
            Fishing::torsoHint,
            Fright::torsoHint);

    /** The reach, from where the leant torso has put the shoulders. */
    public static final List<Step> REACH = List.of(
            (uuid, parts, pass) -> TransportGrip.reach(uuid, parts),
            (uuid, parts, pass) -> HandContacts.reach(uuid, parts),
            (uuid, parts, pass) -> BlockUse.reachContact(uuid, parts),
            (uuid, parts, pass) -> Mining.reach(uuid, parts),
            (uuid, parts, pass) -> Gesture.reach(uuid, parts),
            (uuid, parts, pass) -> ButtonPress.reachContact(uuid, parts),
            (uuid, parts, pass) -> HeavyThrottle.reachContact(uuid, parts),
            (uuid, parts, pass) -> Fishing.reach(uuid, parts));

    /** The hands and the head, from where the shoulders have finally been drawn; the last word on each. */
    public static final List<Step> HANDS = List.of(
            (uuid, parts, pass) -> LookAt.apply(uuid, parts),
            (uuid, parts, pass) -> InteractionRuntime.apply(uuid, parts),
            // The hands on the walls of a narrow gap, from where the turned torso has put the shoulders.
            (uuid, parts, pass) -> WallSqueeze.aimArms(uuid, parts),
            // A hand on a button or a swing on a block aims from where its shoulder has finally been drawn.
            (uuid, parts, pass) -> ButtonPress.aimArm(uuid, parts, pass.base()),
            (uuid, parts, pass) -> TrainCockpit.reach(uuid, parts),
            (uuid, parts, pass) -> BlockUse.aimArm(uuid, parts),
            (uuid, parts, pass) -> Mining.aimArm(uuid, parts, pass.base()),
            (uuid, parts, pass) -> Gesture.aimArms(uuid, parts, pass.base()),
            (uuid, parts, pass) -> PocketStash.aimArm(uuid, parts, pass.base()),
            (uuid, parts, pass) -> CockpitControls.apply(uuid, parts),
            (uuid, parts, pass) -> TrainCockpit.head(uuid, parts),
            (uuid, parts, pass) -> HandContacts.apply(uuid, parts, pass.base(), pass.mainModel()),
            (uuid, parts, pass) -> HeavyThrottle.aimArms(uuid, parts),
            (uuid, parts, pass) -> TransportGrip.aim(uuid, parts),
            (uuid, parts, pass) -> LeadHold.capture(uuid, parts),
            (uuid, parts, pass) -> BoatRide.grip(uuid, parts),
            (uuid, parts, pass) -> BoatPassenger.grip(uuid, parts),
            (uuid, parts, pass) -> MinecartRide.grip(uuid, parts),
            (uuid, parts, pass) -> HorseRide.grip(uuid, parts),
            (uuid, parts, pass) -> FenceLean.grip(uuid, parts),
            (uuid, parts, pass) -> Fishing.grip(uuid, parts),
            (uuid, parts, pass) -> Fright.apply(uuid, parts));

    private BodyStages() {
    }

    /** How many poses each rider draw now under way has pushed: draws do not nest, but a portrait may come in between. */
    private static final Deque<Integer> PUSHED = new ArrayDeque<>();

    /** Before a player is drawn: the {@link #RIDER} moves, in order. */
    public static void beforeRider(net.minecraft.world.entity.player.Player player, PoseStack stack, float partialTick) {
        int pushed = 0;
        for (RiderDraw draw : RIDER) if (draw.before(player, stack, partialTick)) pushed++;
        PUSHED.push(pushed);
    }

    /** After the player is drawn: what {@link #beforeRider} pushed, taken off. */
    public static void afterRider(PoseStack stack) {
        int pushed = PUSHED.isEmpty() ? 0 : PUSHED.pop();
        for (int i = 0; i < pushed; i++) stack.popPose();
    }

    /** Whether a hand is on a control the camera is left free at. */
    public static boolean atControls(AbstractClientPlayer player) {
        for (Predicate<AbstractClientPlayer> at : AT_CONTROLS) if (at.test(player)) return true;
        return false;
    }

    /** Whether a seat turns the whole body itself. */
    public static boolean seatBody(UUID uuid) {
        for (Predicate<UUID> seat : SEAT_BODY) if (seat.test(uuid)) return true;
        return false;
    }

    /** Runs one stage's steps, in order. */
    public static void run(List<Step> stage, UUID uuid, Function<String, ModelPart> parts, Pass pass) {
        for (Step step : stage) step.apply(uuid, parts, pass);
    }
}
