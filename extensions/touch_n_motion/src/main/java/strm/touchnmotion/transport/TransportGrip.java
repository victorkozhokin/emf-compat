package strm.touchnmotion.transport;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import strm.touchnmotion.interaction.*;
import strm.touchnmotion.torso.LowReach;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.ik.IKFrame;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import strm.touchnmotion.interaction.Body;
import strm.touchnmotion.DebugLog;
import strm.touchnmotion.transport.aeronautics.AeronauticRopes;
import strm.touchnmotion.buttonpress.ButtonPress;

/** Automatic nearest free-hand contact while standing on a moving Sable deck. */
public final class TransportGrip implements InteractionProvider {
    public static final TransportGrip INSTANCE = new TransportGrip();
    public static final String KEY_ENABLED = "transport.grip", KEY_TRACE = "transport.trace", KEY_ACCELERATION = "transport.onAcceleration";
    /**
     * Bracing is a reaction to the sub-level changing its speed or its way - speeding up, slowing,
     * turning, bobbing: blocks a second squared of the deck under the player. Over the first a free
     * hand looks for a support; below the second for {@link #CALM_NANOS} it lets go.
     */
    static final double GRIP_ACCELERATION = .8, CALM_ACCELERATION = .35;
    static final long CALM_NANOS = 3_500_000_000L, MIN_HOLD_NANOS = 1_000_000_000L;
    /** Let go at a steady speed, slower than a hand losing its hold. */
    private static final Candidate.Timing CALM_TIMING = new Candidate.Timing(.16, .5, .05);
    private static final EntityStates<State> STATES = new EntityStates<>(State::new);
    private static final Candidate.Timing TIMING = new Candidate.Timing(.16, .18, .05);
    private static final class State {
        AbstractClientPlayer player;
        IKFrame frame;
        SupportSearch.Deck deck;
        SubLevels.Space craft;
        Vec3 reference, previousLocal;
        int localTick = -1;
        double relativeSpeed;
        boolean active, helper;
        float load, ropeBlend;
        boolean ropeCrouching;
        final Vector3f force = new Vector3f(), lastTarget = new Vector3f();
        SupportSearch.Contact primary, other;
        Effector hand = Effector.RIGHT_ARM;
        float gap = 1, helperGap = 1;
        long searchAt, releaseUntil, helperAt;
        /** Since when the deck has kept its speed and its way, and since when a hand has held. */
        long calmSince, heldSince;
        /** The deck's change of speed and way, smoothed over half a second. */
        double change;
        final DebugLog.Pace tracePace = new DebugLog.Pace(), accelPace = new DebugLog.Pace();
        TransportMotion motion = new TransportMotion();
        TransportStance stance = new TransportStance();
        final LowReach.State reach = new LowReach.State();
    }
    public String id() { return "TransportGrip"; }

    @Override
    public String packState() {
        return "tnm_hold";
    }

    @Override
    public boolean holdsBody() {
        return true;
    }
    public boolean isEnabled() { return EMFCompatConfig.getBoolean(KEY_ENABLED, true); }
    public static void register(ConfigRegistry.Group config, ConfigRegistry.Group debug) {
        config.addBoolean(KEY_ENABLED, "Brace on moving sub-levels", true,
                "On", "On a moving Sable sub-level a free hand holds a support nearby, the feet are planted and the body leans against its motion.",
                "Off", "Hands and stance stay as they are on a moving sub-level.");
        config.addBoolean(KEY_ACCELERATION, "Brace only while it speeds up or turns", true,
                "On", "A hand takes hold when the sub-level speeds up, slows down, turns or bobs, and lets go once it has kept its speed and course for a few seconds.",
                "Off", "A hand holds on the whole time the sub-level moves.");
        debug.addBoolean(KEY_TRACE, "Trace sub-level bracing", false, "On", "Log the support and stance checks on moving sub-levels.", "Off", "No sub-level bracing trace.");
    }
    public void collect(InteractionContext context, List<Candidate> out) {
        var p = context.player();
        State s = STATES.seen(p.getUUID(), context.now()).value;
        s.player = p;
        s.frame = context.frame();
        s.active = false;
        s.helper = false;
        if (p.isPassenger() || !p.onGround() || p.isSleeping() || p.isSwimming() || p.isFallFlying() || p.isUsingItem() || strm.touchnmotion.platform.Platform.swinging(p)) {
            release(s, context.now());
            context.decide("off:pose");
            return;
        }
        var spaces = SubLevels.around(p.level(), p.getBoundingBox().inflate(1.2));
        s.deck = SupportSearch.deck(p, spaces);
        if (s.deck == null) { release(s, context.now()); s.craft = null; context.decide("off:no-deck"); return; }
        var craft = s.deck.space();
        if (s.craft == null || !s.craft.same(craft)) {
            s.primary = s.other = null;
            s.craft = craft;
            s.reference = s.deck.local();
            s.motion = new TransportMotion();
            s.stance = new TransportStance();
            s.previousLocal = null;
            s.releaseUntil = 0;
            s.gap = 1;
            s.calmSince = 0;
        }
        Vec3 reference = craft.tickToWorld(s.reference);
        s.motion.sample(p.tickCount, new Vector3d(reference.x, reference.y, reference.z));
        Vec3 local = craft.tickToLocal(p.position());
        if (s.localTick != p.tickCount) {
            s.relativeSpeed = s.previousLocal == null ? 0 : local.subtract(s.previousLocal).horizontalDistance() * 20;
            s.previousLocal = local;
            s.localTick = p.tickCount;
        }
        if (s.motion.warped) { release(s, context.now()); context.decide("off:warp"); return; }
        Vector3d acceleration = s.motion.acceleration;
        Vec3 base = p.position(), end = base.add(acceleration.x, 0, acceleration.z);
        Vector3f force = Body.model(context.frame(), end).sub(Body.model(context.frame(), base)).div(16);
        float wanted = BraceMath.load((float) acceleration.length());
        s.load += (wanted - s.load) * Smoothing.follow(context.dt(), .16);
        if (force.lengthSquared() > 1e-6) force.normalize();
        s.force.lerp(force, Smoothing.follow(context.dt(), .18));
        float ropeWanted = s.primary != null && s.primary.rope() != null ? 1 : 0;
        s.ropeBlend += (ropeWanted - s.ropeBlend) * Smoothing.follow(context.dt(), .2);
        if (s.primary != null && s.primary.rope() != null) {
            if (s.ropeCrouching != p.isCrouching()) {
                var next = AeronauticRopes.find(p, s.frame, spaces, s.hand == Effector.RIGHT_ARM);
                if (next != null) s.primary.rope().retarget(next.rope());
                if (s.other != null && s.other.rope() != null) {
                    var second = AeronauticRopes.find(p, s.frame, spaces, s.hand != Effector.RIGHT_ARM, -.1);
                    if (second != null) s.other.rope().retarget(second.rope());
                }
                s.ropeCrouching = p.isCrouching();
            }
            s.primary.rope().advance(context.dt());
            if (s.other != null && s.other.rope() != null) s.other.rope().advance(context.dt());
        }
        if (s.primary != null && (!free(p, s.hand) || !SupportSearch.valid(p, s.frame, s.primary, s.hand == Effector.RIGHT_ARM))) {
            release(s, context.now());
        }
        // Over half a second: Sable's tick pose jumps now and then, a single tick of a few blocks a
        // second squared on a craft holding its course; a real change of speed or way lasts seconds.
        s.change += (acceleration.length() - s.change) * Smoothing.follow(context.dt(), .5);
        double change = s.change;
        boolean onChange = EMFCompatConfig.getBoolean(KEY_ACCELERATION, true);
        if (change > CALM_ACCELERATION || s.calmSince == 0) s.calmSince = context.now();
        boolean calm = onChange && context.now() - s.calmSince > CALM_NANOS;
        if (EMFCompatConfig.getBoolean(KEY_TRACE, false) && s.accelPace.due(250_000_000L))
            org.slf4j.LoggerFactory.getLogger("EMFCompatTransport").info("[TransportAcceleration] change={} speed={} calm={}s held={}",
                    DebugLog.r2(change), DebugLog.r2(s.motion.speed), DebugLog.r2((context.now() - s.calmSince) / 1e9), s.primary != null);
        if (s.primary != null && calm && context.now() - s.heldSince > MIN_HOLD_NANOS) {
            release(s, context.now());
            context.decide("off:steady");
            return;
        }
        boolean braceWanted = onChange ? change > GRIP_ACCELERATION : s.motion.speed > .15 || s.load > .08;
        if (s.primary == null && context.now() >= s.releaseUntil && context.now() >= s.searchAt && braceWanted) {
            s.searchAt = context.now() + 180_000_000L;
            choose(p, s, spaces);
            if (s.primary != null) s.heldSince = context.now();
        }
        if (s.primary == null) {
            if (EMFCompatConfig.getBoolean(KEY_TRACE, false) && s.tracePace.due(500_000_000L)) {
                org.slf4j.LoggerFactory.getLogger("EMFCompatTransport").info(
                    "[TransportSearch] speed={} load={} deck={} shoulder={}", s.motion.speed, s.load, s.deck.local(),
                    s.frame.jointWorld(new Vector3f(-5, 2, 0)));
            }
            context.decide(onChange && !braceWanted ? "off:steady" : "off:no-support");
            return;
        }
        // Calm for a second already: the hand is offered with a slow fade, so letting go is gentle.
        boolean settling = onChange && context.now() - s.calmSince > 1_000_000_000L;
        offer(context, out, s.hand, s.primary, settling);
        s.active = true;
        // The helper is requested only after the primary palm actually touches.
        // Keep it through small load changes instead of alternating both hands.
        boolean helperWanted = s.gap < .08f && (s.load > (s.other == null ? .65f : .4f));
        Effector other = s.hand.other();
        if (s.other != null && (!helperWanted || !free(p, other) || !SupportSearch.valid(p, s.frame, s.other, other == Effector.RIGHT_ARM))) s.other = null;
        if (helperWanted && s.other == null && free(p, other) && context.now() >= s.helperAt) {
            s.helperAt = context.now() + 200_000_000L;
            var c = s.primary.rope() != null ? AeronauticRopes.find(p, s.frame, spaces, other == Effector.RIGHT_ARM, -.1)
                    :SupportSearch.find(p, s.frame, spaces, other == Effector.RIGHT_ARM);
            if (c != null && c.world().distanceTo(s.primary.world()) > .16) s.other = c;
        }
        if (s.other != null) { offer(context, out, other, s.other, settling); s.helper = true; }
        context.decide((s.helper ? "two-hands" : s.gap < .08 ? "held" : "reaching") + (s.hand == Effector.RIGHT_ARM ? "-R" : "-L"));
    }
    /**
     * The hand for a support, as for buttons and levers ({@link Handedness}): the main one, or with
     * "Nearest hand" the one on the support's side, so an arm does not reach across the chest for a
     * rope beside the other shoulder. A hand that holds something, or finds nothing, gives way to
     * the other. The main hand looks first; the other only when the support is on its side.
     */
    private static void choose(AbstractClientPlayer p, State s, List<SubLevels.Space> spaces) {
        boolean mainRight = Body.right(p, true);
        Effector main = Effector.arm(mainRight), other = main.other();
        boolean mainFree = free(p, main), otherFree = free(p, other);
        Effector hand = mainFree ? main : otherFree ? other : null;
        if (hand == null) return;
        var c = SupportSearch.find(p, s.frame, spaces, hand == Effector.RIGHT_ARM);
        if (c == null && hand == main && otherFree) {
            hand = other;
            c = SupportSearch.find(p, s.frame, spaces, hand == Effector.RIGHT_ARM);
        }
        if (c == null) return;
        if (hand == main && otherFree && EMFCompatConfig.getBoolean(ButtonPress.KEY_NEAREST, true)) {
            Vector3f at = Body.model(s.frame, c.world());
            boolean right = Handedness.right(at.x, at.z, mainRight, null, false, false, true, true, true);
            if (right != (main == Effector.RIGHT_ARM)) {
                var near = SupportSearch.find(p, s.frame, spaces, right);
                if (near != null) { hand = other; c = near; }
            }
        }
        s.primary = c;
        s.hand = hand;
        s.gap = 1;
        s.ropeCrouching = p.isCrouching();
    }

    private static boolean free(AbstractClientPlayer p, Effector hand) {
        boolean main = Body.right(p, true) == (hand == Effector.RIGHT_ARM);
        return p.getItemInHand(main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND).isEmpty()
                && InteractionRuntime.weight(p.getUUID(), hand, "LeadHold") < .05f;
    }
    private static void release(State s, long now) {
        if (s.primary != null) s.releaseUntil = now + 350_000_000L;
        s.primary = s.other = null;
        s.gap = s.helperGap = 1;
        s.active = false;
    }
    private static void offer(InteractionContext context, List<Candidate> out, Effector hand, SupportSearch.Contact c, boolean settling) {
        HandContacts.remember(context, INSTANCE.id(), hand, c);
        Vector3f point = context.frame().relativeToJoint(c.world(), new Vector3f(hand == Effector.RIGHT_ARM ? -5 : 5, 2, 0)).normalize();
        var a = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), point).getEulerAnglesZYX(new Vector3f());
        out.add(Candidate.single(INSTANCE.id(), Category.PASSIVE, 25, 1, settling ? CALM_TIMING : TIMING, hand, new float[]{a.x, a.y}).withTarget(c.rope() != null ? c.rope().id :
                new strm.touchnmotion.interaction.ContactTarget(c.space(), c.block(), context.player().level().getBlockState(c.block()).getBlock())));
    }
    private static float ownership(UUID uuid, State s) {
        return s.active && INSTANCE.isEnabled() ? InteractionRuntime.weight(uuid, s.hand, INSTANCE.id()) : 0;
    }
    public static float[] torsoHint(UUID uuid) {
        State s = STATES.fresh(uuid);
        if (s == null || !s.active || s.gap >= .08) return null;
        float owned = ownership(uuid, s), rope = s.ropeBlend * owned;
        float effort = s.load * owned, gain = 1 + s.ropeBlend * (RopePoseMath.gain(s.motion.speed) - 1);
        float side = s.hand == Effector.RIGHT_ARM ? -1 : 1;
        Vector3f lean = new Vector3f(s.lastTarget.x, 0, s.lastTarget.z);
        if (lean.lengthSquared() > .01f) lean.normalize();
        float edge = s.stance.ropeLift / 1.8f * owned;
        return new float[]{s.force.z * (float) Math.toRadians(9 + 7 * s.ropeBlend) * effort * gain + (float) Math.toRadians(5) * rope - lean.z * (float) Math.toRadians(10) * edge,
                0, s.force.x * (float) Math.toRadians(10 + 6 * s.ropeBlend) * effort * gain - side * (float) Math.toRadians(3) * rope + lean.x * (float) Math.toRadians(8) * edge,
                -s.force.x * (.7f + .5f * s.ropeBlend) * effort};
    }
    public static void support(UUID uuid, Function<String, ModelPart> parts) {
        State s = STATES.fresh(uuid);
        if (s == null || s.player == null) return;
        // Native rope interpolation may briefly move the palm by a few pixels.
        // Keep the settled soles through that small error instead of restarting both steps.
        float tolerance = s.primary != null && s.primary.rope() != null ? .18f : .08f;
        float owned = s.gap < tolerance ? ownership(uuid, s) : 0;
        s.stance.apply(s.player, s.frame, s.deck, parts, s.force, s.load, owned, s.relativeSpeed > .2, s.ropeBlend, s.hand == Effector.RIGHT_ARM, s.helper, s.primary == null ? null : s.primary.world());
    }
    public static void reach(UUID uuid, Function<String, ModelPart> parts) {
        State s = STATES.fresh(uuid);
        if (s == null || s.frame == null) return;
        float owned = ownership(uuid, s);
        if (s.primary == null) {
            if (s.reach.active()) LowReach.apply(parts, s.hand == Effector.RIGHT_ARM, s.lastTarget, 0, s.reach);
            return;
        }
        Vector3f point = Body.model(s.frame, s.primary.world());
        s.lastTarget.set(point);
        Vector3f other = s.helper && s.other != null ? Body.model(s.frame, s.other.world()) : null;
        s.reach.angleLimit = (float) Math.toRadians(12 + 6 * s.ropeBlend);
        s.reach.followSeconds = .16;
        s.reach.weightShift = (s.hand == Effector.RIGHT_ARM ? -.45f : .45f) * s.ropeBlend;
        s.reach.weightForward = -.7f * s.ropeBlend;
        LowReach.apply(parts, s.hand == Effector.RIGHT_ARM, point, other, owned, s.reach);
    }
    public static void aim(UUID uuid, Function<String, ModelPart> parts, boolean mainModel) {
        State s = STATES.fresh(uuid);
        if (s == null || s.frame == null) return;
        HandContacts.applyMoving(uuid, INSTANCE.id(), parts, mainModel);
        if (!mainModel || !s.active) return;
        s.gap = HandContacts.gap(uuid, INSTANCE.id(), s.hand, parts);
        if (s.helper) s.helperGap = HandContacts.gap(uuid, INSTANCE.id(), s.hand.other(), parts);
        if (EMFCompatConfig.getBoolean(KEY_TRACE, false) && s.tracePace.due(100_000_000L)) {
            org.slf4j.LoggerFactory.getLogger("EMFCompatTransport").info(
                "[TransportTrace] speed={} relative={} load={} owned={} gap={} helper={} helperGap={} step={} right={} left={} local={} world={} rope={} lift={}",
                s.motion.speed, s.relativeSpeed, s.load, ownership(uuid, s), s.gap, s.helper, s.helperGap,
                s.stance.stride.stepping, s.stance.stride.feet[0], s.stance.stride.feet[1], s.primary.point(), s.primary.world(), s.primary.rope() != null, s.stance.ropeLift);
        }
    }
}
