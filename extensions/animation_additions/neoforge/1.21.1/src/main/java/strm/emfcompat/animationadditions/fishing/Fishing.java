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
import strm.emfcompat.animationadditions.interaction.ArmAim;
import strm.emfcompat.animationadditions.interaction.Body;
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.Category;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.animationadditions.interaction.InteractionProvider;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.animationadditions.mixin.FishingHookAccessor;
import strm.emfcompat.animationadditions.motion.Spring;
import strm.emfcompat.animationadditions.ride.BoatRide;
import strm.emfcompat.animationadditions.torso.BraceSteps;
import strm.emfcompat.animationadditions.torso.LowReach;
import strm.emfcompat.animationadditions.torso.TorsoLean;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKResult;
import strm.emfcompat.core.ik.OneBoneIK;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Fishing: the cast, the wait, the bite and the haul, each set off by what the float really does.
 * The click (or the float appearing, for another player) is the cast; while the float rides the
 * water the rod is held out over it; the server's word of a bite brings the second hand to the
 * rod and the weight forward; the float gone is the line brought in - heaved with both hands
 * after a bite, lifted in one without - and then the rod let down before the body, a pause over
 * the catch if there is one, before the pose is given up.
 *
 * <p>Built as the heavy throttle is: what is driven is the place of the rod hand
 * ({@link FishingMotion}), quick, on a spring; the body is then fitted to that place after the
 * fact, late and soft ({@link LowReach}: a turn from the hips, a shift of the pelvis with the
 * soles kept, the weight sent forward or back with the effort), and the arm is aimed last from
 * where the shoulder has ended up. The feet are set by {@link BraceSteps}; the second hand is
 * brought onto the rod under the first; the free arm is held a little out from the body and swung
 * against the rod's. The float is any player's, and the bite is sent to every client, so it shows
 * for others too. Sitting it is left alone.</p>
 */
public final class Fishing implements InteractionProvider {

    public static final Fishing INSTANCE = new Fishing();
    public static final String KEY_ENABLED = "fishing.enabled", KEY_LINE = "fishing.lineOnTip";
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatFishing");

    private static final Candidate.Timing TIMING = new Candidate.Timing(0.08, 0.07, 0.02);
    /** Seconds: a bite this long ago still makes the haul a heave; the body's coming after the hand; the weight's going over. */
    private static final double FRESH_BITE = 1.5, BODY_SECONDS = 0.17, WEIGHT_SECONDS = 0.13;
    /** Model pixels the rod hand is carried to the side after the float, for a float square off to that side. */
    private static final float BEARING = 5f;
    private static final Vector3f HOME = new Vector3f();
    /** Model pixels: the second hand takes the rod this far under the first. */
    private static final float UNDER = 2.2f;
    /** Radians: the free arm held out from the body, and how far it swings. */
    private static final float FREE_OUT = 0.3f, FREE_SWING = 0.55f;
    /** The rod's tip against the arm that holds it, model pixels in the arm's own space (y runs down the arm, -z before it). */
    private static final Vector3f TIP = new Vector3f(0f, 8.5f, -12f);

    private enum Phase { NONE, CAST, WAIT, BITE, HAUL, EASE }

    private static final class State {
        Phase phase = Phase.NONE;
        long since, bitten;
        boolean hadHook, hooked, right, standing, swung, twoHands;
        /** The rod hand's place, model pixels, each axis on its spring; set once the pose begins. */
        final Spring x = new Spring(), y = new Spring(), z = new Spring();
        boolean placed;
        float weight, bearing, freeSwing, shown, turn, forward, aside;
        int stance;
        final Vector3f point = new Vector3f();
        AbstractClientPlayer player;
        IKFrame frame;
        final BraceSteps.State feet = new BraceSteps.State();
        final LowReach.State reach = new LowReach.State();
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
        // One's own cast begins with the click, not with the float the server sends back a tick or two later.
        var mc = net.minecraft.client.Minecraft.getInstance();
        boolean clicked = player == mc.player && player.swinging && !state.swung && mc.options.keyUse.isDown();
        state.swung = player.swinging;
        if (!able) {
            state.phase = Phase.NONE;
        } else if (clicked && hook == null && (state.phase == Phase.NONE || state.phase == Phase.EASE)) {
            enter(state, Phase.CAST, now);
        } else if (hook != null && !state.hadHook) {
            if (state.phase != Phase.CAST) enter(state, Phase.CAST, now);
        } else if (hook == null && state.hadHook) {
            // The line brought in: a heave if something was on it a moment ago.
            state.hooked = (now - state.bitten) / 1e9 < FRESH_BITE;
            enter(state, Phase.HAUL, now);
        } else if (state.phase == Phase.CAST && in >= FishingMotion.CAST) {
            // A click that cast nothing - the rod swung at the air - is over with the swing.
            if (hook == null) state.phase = Phase.NONE;
            else enter(state, Phase.WAIT, now);
        } else if (state.phase == Phase.WAIT && biting) {
            enter(state, Phase.BITE, now);
        } else if (state.phase == Phase.BITE && !biting) {
            enter(state, Phase.WAIT, now);
        } else if (state.phase == Phase.HAUL && in >= (state.hooked ? FishingMotion.HAUL : FishingMotion.LIFT)) {
            // Not straight back to standing: the rod is let down first.
            enter(state, Phase.EASE, now);
        } else if (state.phase == Phase.EASE && in >= (state.hooked ? FishingMotion.EASE : FishingMotion.EASE_EMPTY)) {
            state.phase = Phase.NONE;
        } else if (state.phase == Phase.NONE && hook != null) {
            // Come upon with the line already out: another player's, or one's own after a ride.
            enter(state, Phase.WAIT, now);
        }
        state.hadHook = hook != null && able;
        if (state.phase == Phase.NONE) {
            state.shown += -state.shown * Smoothing.follow(dt, 0.1);
            state.weight += -state.weight * Smoothing.follow(dt, WEIGHT_SECONDS);
            state.freeSwing += -state.freeSwing * Smoothing.follow(dt, 0.12);
            state.turn += -state.turn * Smoothing.follow(dt, 0.2);
            state.forward += -state.forward * Smoothing.follow(dt, 0.2);
            state.aside += -state.aside * Smoothing.follow(dt, 0.2);
            state.twoHands = false;
            state.placed = false;
            context.decide(hand == null ? "off" : able ? "idle" : "off:state");
            return;
        }
        state.right = Body.right(player, hand);
        state.standing = Body.planted(player);
        in = (float) ((now - state.since) / 1e9);
        FishingMotion.Aim aim = switch (state.phase) {
            case CAST -> FishingMotion.cast(in);
            case BITE -> FishingMotion.bite(in);
            case HAUL -> FishingMotion.haul(in, state.hooked);
            case EASE -> FishingMotion.ease(in, state.hooked);
            default -> FishingMotion.waiting(in);
        };
        state.turn += (aim.turn() - state.turn) * Smoothing.follow(dt, 0.14);
        state.forward += (aim.forward() - state.forward) * Smoothing.follow(dt, 0.3);
        state.aside += (aim.aside() - state.aside) * Smoothing.follow(dt, 0.3);
        state.stance = aim.stance();
        // The rod is swept round with the body: its place turned to the rod's side by as much (to the right, -x, for a turn above zero).
        float sin = Mth.sin(state.turn), cos = Mth.cos(state.turn);
        Vector3f swept = new Vector3f(aim.point().x * cos + aim.point().z * sin, aim.point().y, -aim.point().x * sin + aim.point().z * cos);
        Vector3f wanted = FishingMotion.sided(swept, state.right);
        // The rod carried after the float: its bearing against the body, model space (forward is -z, the right is -x).
        float bearing = 0f;
        if (hook != null && state.phase != Phase.CAST) {
            Vector3f at = Body.model(context.frame(), hook.position());
            bearing = Mth.clamp((float) Math.atan2(at.x, -at.z), -1f, 1f);
        }
        state.bearing += (bearing - state.bearing) * Smoothing.follow(dt, 0.25);
        wanted.x += state.bearing * BEARING * (wanted.z < 0 ? 1f : 0f);
        if (!state.placed) {
            // From where the hand hangs, so the first move is a move and not a jump.
            state.x.set(state.right ? -6f : 6f);
            state.y.set(11f);
            state.z.set(0f);
            state.placed = true;
        }
        state.x.update(wanted.x, aim.quick(), dt);
        state.y.update(wanted.y, aim.quick(), dt);
        state.z.update(wanted.z, aim.quick(), dt);
        state.point.set(state.x.value, state.y.value, state.z.value);
        state.weight += (aim.weight() - state.weight) * Smoothing.follow(dt, WEIGHT_SECONDS);
        state.freeSwing += (aim.freeSwing() - state.freeSwing) * Smoothing.follow(dt, 0.09);
        state.twoHands = aim.twoHands();
        state.shown += (1f - state.shown) * Smoothing.follow(dt, 0.08);

        // The game's own swing of the rod is this pose's to replace.
        context.claimArms();
        offer(context, out, state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, state.point);
        if (state.twoHands) offer(context, out, state.right ? Effector.LEFT_ARM : Effector.RIGHT_ARM, second(state));
        context.decide(state.phase == Phase.HAUL ? state.hooked ? "haul" : "lift" : state.phase.name().toLowerCase());
    }

    /** An arm sent to a place in the model; the last aim, once the body has come after it, is {@link #grip}'s. */
    private void offer(InteractionContext context, List<Candidate> out, Effector arm, Vector3f point) {
        Vector3f shoulder = new Vector3f(arm == Effector.RIGHT_ARM ? -5 : 5, 2, 0);
        IKResult aim = OneBoneIK.solveXY(context.frame(), shoulder, context.frame().jointWorld(new Vector3f(point)), 11, 0, 0);
        if (aim != null) out.add(Candidate.single(id(), Category.ACTIVE, 10, 1f, TIMING, arm, new float[]{aim.x(), aim.y()}).withQuietSwing(true));
    }

    /** Where the second hand takes the rod: under the first, towards the body's middle. */
    private static Vector3f second(State state) {
        return new Vector3f(state.point).add(state.right ? 1.2f : -1.2f, UNDER, 0.6f);
    }

    private static void enter(State state, Phase phase, long now) {
        state.phase = phase;
        state.since = now;
    }

    /**
     * What fishing asks of the torso before it is fitted to the rod hand: the turn to the rod's
     * side as the line comes in, and the lean of the wait - forward, and away from the rod.
     */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || !INSTANCE.isEnabled()) return null;
        if (Math.abs(state.turn) + Math.abs(state.forward) + Math.abs(state.aside) < 1e-3f) return null;
        // +yRot takes the right shoulder back - the chest to the right; +zRot leans the body to its left.
        float side = state.right ? 1f : -1f;
        return TorsoLean.Hint.turn(state.forward, side * state.turn, side * state.aside);
    }

    /** The feet, before the torso: the foot on the rod's side set back, the other forward, and shifted a step wider at the bite and again at the haul. */
    public static void support(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || state.player == null || !INSTANCE.isEnabled()) return;
        if (state.shown < 0.05f && state.feet.resting()) return;
        boolean apart = state.phase != Phase.NONE && state.standing;
        BraceSteps.apply(state.feet, state.player, state.frame, parts,
                apart ? FishingMotion.foot(state.stance, true, state.right) : HOME, apart ? FishingMotion.foot(state.stance, false, state.right) : HOME,
                apart ? state.shown : 0f, 0f, LOGGER, "FishingStance");
    }

    /**
     * The body brought after the rod hand, once the ordinary torso layers are done: turned from the
     * hips and shifted over the soles until the arm can reach where the hand is wanted, with the
     * weight sent forward or back by the effort - and all of it a beat behind the hand.
     */
    public static void reach(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !INSTANCE.isEnabled()) return;
        float owned = state.phase == Phase.NONE ? 0f
                : InteractionRuntime.weight(uuid, state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, INSTANCE.id());
        if (owned < 1e-3f && !state.reach.active()) return;
        state.reach.followSeconds = BODY_SECONDS;
        state.reach.angleLimit = (float) Math.toRadians(34);
        // Forward is -z.
        state.reach.weightForward = state.standing ? -state.weight : 0f;
        state.reach.weightShift = 0f;
        boolean both = state.twoHands && InteractionRuntime.weight(uuid, state.right ? Effector.LEFT_ARM : Effector.RIGHT_ARM, INSTANCE.id()) > 0.5f;
        LowReach.apply(parts, state.right, state.point, both ? second(state) : null, owned, state.reach);
    }

    /** The last word on the arms: each aimed at its place from where the shoulder has ended up, and the free arm off the body. */
    public static void grip(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !INSTANCE.isEnabled()) return;
        ModelPart rod = parts.apply(state.right ? "right_arm" : "left_arm"), other = parts.apply(state.right ? "left_arm" : "right_arm");
        if (rod == null || other == null) return;
        float owned = InteractionRuntime.weight(uuid, state.right ? Effector.RIGHT_ARM : Effector.LEFT_ARM, INSTANCE.id());
        float second = InteractionRuntime.weight(uuid, state.right ? Effector.LEFT_ARM : Effector.RIGHT_ARM, INSTANCE.id());
        if (owned >= 0.02f) ArmAim.towards(rod, state.point, owned, true);
        if (second >= 0.02f) {
            BoatRide.settle(other, second(state), second);
        } else if (state.shown > 1e-3f) {
            // The free arm: out from the body is +zRot for the right arm and -zRot for the left; swung forward is -xRot.
            float free = state.shown * (1f - second);
            other.zRot += (state.right ? -1f : 1f) * FREE_OUT * free;
            other.xRot += -state.freeSwing * FREE_SWING * free;
        }
        if (state.phase == Phase.NONE) return;
        if (state.frame != null) {
            Vector3f tip = new Quaternionf().rotationZYX(rod.zRot, rod.yRot, rod.xRot).transform(new Vector3f(TIP)).add(rod.x, rod.y, rod.z);
            state.tip = state.frame.jointWorld(tip).subtract(state.player.getPosition(net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)));
        }
        if (DebugLog.trace() && state.trace.due(40_000_000L)) {
            ModelPart body = parts.apply("body");
            LOGGER.info("[FishTrace] phase={} hooked={} rodPitch={} rodYaw={} second={} bodyPitch={} bodyYaw={} bodyZ={} weight={} miss={}", state.phase, state.hooked,
                    Math.round(Math.toDegrees(rod.xRot)), Math.round(Math.toDegrees(rod.yRot)), Math.round(second * 100) / 100f,
                    body == null ? 0 : Math.round(Math.toDegrees(body.xRot)), body == null ? 0 : Math.round(Math.toDegrees(body.yRot)),
                    body == null ? 0 : Math.round(body.z * 10) / 10f, Math.round(state.weight * 10) / 10f,
                    Math.round(Body.tip(rod, 11f).distance(state.point) * 10) / 10f);
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
