package strm.emfcompat.animationadditions.fishing;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.animationadditions.DebugLog;
import strm.emfcompat.animationadditions.interaction.Body;
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.Category;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.animationadditions.interaction.InteractionProvider;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.mixin.FishingHookAccessor;
import strm.emfcompat.animationadditions.motion.Spring;
import strm.emfcompat.animationadditions.ride.BoatRide;
import strm.emfcompat.animationadditions.torso.BraceSteps;
import strm.emfcompat.animationadditions.torso.PelvisFollow;
import strm.emfcompat.animationadditions.torso.TorsoLean;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.ik.IKFrame;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Fishing: the cast, the wait, the bite and the haul, each set off by what the float really does.
 * The float appearing is the cast; while it rides the water the rod is held out over it; the
 * server's word of a bite brings the second hand to the rod and the body forward; the float gone
 * is the line brought in - heaved with both hands after a bite, lifted in one without.
 *
 * <p>The shape of it is {@link FishingMotion}; here it is hung on the game: which arm has the rod
 * (either, mirrored), the float's bearing, the feet set for it by {@link BraceSteps} and the hips
 * with them, the torso through {@link TorsoLean}, and the second hand brought onto the rod below
 * the first by the same last fit the riders' hands have. The float is any player's, and the bite
 * is sent to every client, so it shows for others too. Sitting it is left alone.</p>
 */
public final class Fishing implements InteractionProvider {

    public static final Fishing INSTANCE = new Fishing();
    public static final String KEY_ENABLED = "fishing.enabled", KEY_LINE = "fishing.lineOnTip";
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatFishing");

    private static final Candidate.Timing TIMING = new Candidate.Timing(0.06, 0.07, 0.02);
    /** Seconds: the pose follows its shape through springs of this half-life; a bite this long ago still makes the haul a heave. */
    private static final double SPRING = 0.035, FRESH_BITE = 1.5;
    /** Radians: as far as the rod is turned after the float, and the share of the float's bearing it takes. */
    private static final float BEARING_LIMIT = 0.45f, BEARING_SHARE = 0.6f;
    /** Model pixels from where the pack has the soles (back is +z, the right is -x): the foot on the rod's side back, the other forward. */
    private static final Vector3f BACK_RIGHT = new Vector3f(-0.6f, 0f, 2.4f), FRONT_LEFT = new Vector3f(0.3f, 0f, -1.6f),
            BACK_LEFT = new Vector3f(0.6f, 0f, 2.4f), FRONT_RIGHT = new Vector3f(-0.3f, 0f, -1.6f), HOME = new Vector3f();
    /** Model pixels down the rod arm from its shoulder: where the second hand takes the rod, just under the first. */
    private static final float SECOND_HAND = 8.5f;
    /** The rod's tip against the arm that holds it, model pixels in the arm's own space (y runs down the arm, -z before it). */
    private static final Vector3f TIP = new Vector3f(0f, 8.5f, -12f);

    private enum Phase { NONE, CAST, WAIT, BITE, HAUL }

    private static final class State {
        Phase phase = Phase.NONE;
        long since, bitten;
        boolean hadHook, hooked, right, standing;
        FishingMotion.Pose from = FishingMotion.WAIT;
        final Spring armPitch = new Spring(), armIn = new Spring(), torsoPitch = new Spring(), torsoTurn = new Spring(),
                hips = new Spring(), helper = new Spring(), bearing = new Spring();
        float shown;
        AbstractClientPlayer player;
        IKFrame frame;
        final BraceSteps.State stance = new BraceSteps.State();
        /** The rod's tip, blocks from the player's own place, as last drawn. */
        Vec3 tip;
        final DebugLog.Pace trace = new DebugLog.Pace();
    }

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private Fishing() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Fishing", true,
                "On", "Casting, waiting, a bite and bringing the line in are each played with the whole body.",
                "Off", "Leave fishing to EMF.");
        config.addChild(KEY_ENABLED, KEY_LINE, "Line from the rod's tip (Enchanted Fishing Line)", true,
                "On", "With Enchanted Fishing Line installed, the line starts at the rod's tip as the pose has it.",
                "Off", "The line starts where the game puts it. Without that mod it always does.");
    }

    @Override
    public String id() {
        return "Fishing";
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
        FishingHook hook = player.fishing != null && player.fishing.isAlive() ? player.fishing : null;
        InteractionHand hand = player.getMainHandItem().getItem() instanceof FishingRodItem ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().getItem() instanceof FishingRodItem ? InteractionHand.OFF_HAND : null;
        boolean able = hand != null && !player.isPassenger() && !player.isSleeping() && !player.isSwimming()
                && (player.getPose() == Pose.STANDING || player.getPose() == Pose.CROUCHING);
        double dt = context.dt();
        state.player = player;
        state.frame = context.frame();

        boolean biting = hook != null && ((FishingHookAccessor) hook).emfcompat$biting();
        if (biting) state.bitten = now;
        float in = (float) ((now - state.since) / 1e9);
        if (!able) {
            state.phase = Phase.NONE;
        } else if (hook != null && !state.hadHook) {
            enter(state, Phase.CAST, now);
        } else if (hook == null && state.hadHook) {
            // The line brought in: a heave if something was on it a moment ago.
            state.hooked = (now - state.bitten) / 1e9 < FRESH_BITE;
            state.from = shown(state);
            enter(state, Phase.HAUL, now);
        } else if (state.phase == Phase.CAST && in >= FishingMotion.CAST) {
            enter(state, Phase.WAIT, now);
        } else if (state.phase == Phase.WAIT && biting) {
            enter(state, Phase.BITE, now);
        } else if (state.phase == Phase.BITE && !biting) {
            enter(state, Phase.WAIT, now);
        } else if (state.phase == Phase.HAUL && in >= (state.hooked ? FishingMotion.HAUL : FishingMotion.LIFT)) {
            state.phase = Phase.NONE;
        } else if (state.phase == Phase.NONE && hook != null) {
            // Come upon with the line already out: another player's, or one's own after a ride.
            enter(state, Phase.WAIT, now);
        }
        state.hadHook = hook != null && able;
        if (state.phase == Phase.NONE) {
            state.shown += -state.shown * (float) Math.min(1.0, dt / 0.12);
            context.decide(hand == null ? "off" : able ? "idle" : "off:state");
            return;
        }
        state.right = Body.right(player, hand);
        state.standing = Body.planted(player);
        in = (float) ((now - state.since) / 1e9);
        FishingMotion.Pose pose = switch (state.phase) {
            case CAST -> FishingMotion.cast(in);
            case BITE -> FishingMotion.bite(in);
            case HAUL -> FishingMotion.haul(state.from, in, state.hooked);
            default -> FishingMotion.waiting(in);
        };
        // The rod turned after the float: its bearing against the body, model space (forward is -z, the right is -x).
        float bearing = 0f;
        if (hook != null && state.phase != Phase.CAST) {
            Vector3f at = Body.model(context.frame(), hook.position());
            bearing = Mth.clamp((float) Math.atan2(-at.x, -at.z) * BEARING_SHARE, -BEARING_LIMIT, BEARING_LIMIT);
        }
        state.armPitch.update(pose.armPitch(), SPRING, dt);
        state.armIn.update(pose.armIn(), SPRING, dt);
        state.torsoPitch.update(pose.torsoPitch(), SPRING, dt);
        state.torsoTurn.update(pose.torsoTurn(), SPRING, dt);
        state.hips.update(pose.hips(), SPRING * 2, dt);
        state.helper.update(pose.helper(), SPRING, dt);
        state.bearing.update(bearing, 0.1, dt);
        state.shown += (1f - state.shown) * (float) Math.min(1.0, dt / 0.1);

        // The game's own swing of the rod is this pose's to replace.
        context.claimArms();
        // Turned out to the right is +yRot for either arm; in towards the middle is the other way for each.
        float side = state.right ? -1f : 1f;
        float[] rod = {state.armPitch.value, side * state.armIn.value + state.bearing.value};
        out.add(Candidate.single(id(), Category.ACTIVE, 10, 1f, TIMING, state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, rod).withQuietSwing(true));
        if (state.helper.value > 0.3f) {
            // Roughly at the rod; the last fit puts the fist on it.
            float[] second = {state.armPitch.value + 0.2f, -side * 0.8f + state.bearing.value};
            out.add(Candidate.single(id(), Category.ACTIVE, 10, 1f, TIMING, state.right ? Effector.LEFT_ARM : Effector.RIGHT_ARM, second).withQuietSwing(true));
        }
        context.decide(state.phase == Phase.HAUL ? state.hooked ? "haul" : "lift" : state.phase.name().toLowerCase());
    }

    private static void enter(State state, Phase phase, long now) {
        state.phase = phase;
        state.since = now;
    }

    private static FishingMotion.Pose shown(State state) {
        return new FishingMotion.Pose(state.armPitch.value, state.armIn.value, state.torsoPitch.value, state.torsoTurn.value,
                state.hips.value, state.helper.value);
    }

    /** What fishing asks of the torso; {@code null} with no line out. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || state.shown < 1e-3f || !INSTANCE.isEnabled()) return null;
        // Taking the right shoulder back is +yRot.
        return TorsoLean.Hint.turn(state.torsoPitch.value * state.shown, (state.right ? 1f : -1f) * state.torsoTurn.value * state.shown, 0f);
    }

    /** The feet, before the torso: the foot on the rod's side set back, the other forward - a step each - and the hips over one or the other as the weight goes. */
    public static void support(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || state.player == null || !INSTANCE.isEnabled()) return;
        if (state.shown < 0.05f && state.stance.resting()) return;
        boolean apart = state.phase != Phase.NONE && state.standing;
        BraceSteps.apply(state.stance, state.player, state.frame, parts,
                !apart ? HOME : state.right ? BACK_RIGHT : FRONT_RIGHT, !apart ? HOME : state.right ? FRONT_LEFT : BACK_LEFT,
                apart ? state.shown : 0f, 0f, LOGGER, "FishingStance");
        if (state.standing) PelvisFollow.shift(parts, 0f, state.hips.value * state.shown);
    }

    /** The last word on the hands: the second one brought onto the rod under the first, and where the rod's tip has come to. */
    public static void grip(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || state.phase == Phase.NONE || !INSTANCE.isEnabled()) return;
        ModelPart rod = parts.apply(state.right ? "right_arm" : "left_arm"), second = parts.apply(state.right ? "left_arm" : "right_arm");
        if (rod == null || second == null) return;
        float weight = InteractionRuntime.weight(uuid, state.right ? Effector.LEFT_ARM : Effector.RIGHT_ARM, INSTANCE.id()) * Mth.clamp(state.helper.value, 0f, 1f);
        if (weight >= 0.02f) BoatRide.settle(second, Body.tip(rod, SECOND_HAND), weight);
        if (state.frame != null) {
            Vector3f tip = new Quaternionf().rotationZYX(rod.zRot, rod.yRot, rod.xRot).transform(new Vector3f(TIP)).add(rod.x, rod.y, rod.z);
            state.tip = state.frame.jointWorld(tip).subtract(state.player.getPosition(net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)));
        }
        if (DebugLog.trace() && state.trace.due(50_000_000L)) {
            ModelPart body = parts.apply("body");
            LOGGER.info("[FishTrace] phase={} hooked={} rodPitch={} rodYaw={} second={} bodyPitch={} bodyYaw={} hips={}", state.phase, state.hooked,
                    Math.round(Math.toDegrees(rod.xRot)), Math.round(Math.toDegrees(rod.yRot)), Math.round(weight * 100) / 100f,
                    body == null ? 0 : Math.round(Math.toDegrees(body.xRot)), body == null ? 0 : Math.round(Math.toDegrees(body.yRot)), state.hips.value);
        }
    }

    /**
     * Where the line starts for {@code player}, in the world - the rod's tip as the pose has it -
     * or {@code null} to leave it where the game puts it: no pose, the option off, or no Enchanted
     * Fishing Line (asked for by the user for that mod only).
     */
    public static Vec3 lineStart(Player player, float partial) {
        if (!LINE_MOD || !EMFCompatConfig.getBoolean(KEY_LINE, true) || !INSTANCE.isEnabled()) return null;
        State state = STATES.fresh(player.getUUID());
        if (state == null || state.phase == Phase.NONE || state.tip == null) return null;
        return player.getPosition(partial).add(state.tip);
    }

    private static final boolean LINE_MOD = net.neoforged.fml.ModList.get().isLoaded("enchanted_fishing_line");
}
