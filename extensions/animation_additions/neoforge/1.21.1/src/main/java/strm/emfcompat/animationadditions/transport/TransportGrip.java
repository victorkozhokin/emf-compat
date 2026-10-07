package strm.emfcompat.animationadditions.transport;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.*;
import strm.emfcompat.animationadditions.torso.LowReach;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.ik.IKFrame;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import strm.emfcompat.animationadditions.interaction.Body;

/** Automatic nearest free-hand contact while standing on a moving Sable deck. */
public final class TransportGrip implements InteractionProvider {
    public static final TransportGrip INSTANCE = new TransportGrip();
    public static final String KEY_ENABLED = "transport.grip", KEY_TRACE = "transport.trace";
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
        long searchAt, releaseUntil, helperAt, traceAt;
        TransportMotion motion = new TransportMotion();
        TransportStance stance = new TransportStance();
        final LowReach.State reach = new LowReach.State();
    }
    public String id() { return "TransportGrip"; }
    public boolean isEnabled() { return EMFCompatConfig.getBoolean(KEY_ENABLED, true); }
    public static void register(ConfigRegistry.Group config, ConfigRegistry.Group debug) {
        config.addBoolean(KEY_ENABLED, "Brace on moving transport", true,
                "On", "Hold a nearby support with a free hand, plant the feet and counterbalance craft motion.",
                "Off", "Leave hands and stance unchanged on moving decks.");
        debug.addBoolean(KEY_TRACE, "Trace transport contacts", false, "On", "Log transport contact and stance checks.", "Off", "No transport trace.");
    }
    public void collect(InteractionContext context, List<Candidate> out) {
        var p = context.player();
        State s = STATES.seen(p.getUUID(), context.now()).value;
        s.player = p;
        s.frame = context.frame();
        s.active = false;
        s.helper = false;
        if (p.isPassenger() || !p.onGround() || p.isSleeping() || p.isSwimming() || p.isFallFlying() || p.isUsingItem() || p.swinging) {
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
        if (s.primary == null && context.now() >= s.releaseUntil && context.now() >= s.searchAt && (s.motion.speed > .15 || s.load > .08)) {
            s.searchAt = context.now() + 180_000_000L;
            for (Effector hand : new Effector[]{Effector.RIGHT_ARM, Effector.LEFT_ARM}) if (free(p, hand)) {
                var c = SupportSearch.find(p, s.frame, spaces, hand == Effector.RIGHT_ARM);
                if (c != null) { s.primary = c; s.hand = hand; s.gap = 1; s.ropeCrouching = p.isCrouching(); break; }
            }
        }
        if (s.primary == null) {
            if (EMFCompatConfig.getBoolean(KEY_TRACE, false) && context.now() - s.traceAt > 500_000_000L) {
                s.traceAt = context.now();
                org.slf4j.LoggerFactory.getLogger("EMFCompatTransport").info(
                    "[TransportSearch] speed={} load={} deck={} shoulder={}", s.motion.speed, s.load, s.deck.local(),
                    s.frame.jointWorld(new Vector3f(-5, 2, 0)));
            }
            context.decide("off:no-support");
            return;
        }
        offer(context, out, s.hand, s.primary);
        s.active = true;
        // The helper is requested only after the primary palm actually touches.
        // Keep it through small load changes instead of alternating both hands.
        boolean helperWanted = s.gap < .08f && (s.load > (s.other == null ? .65f : .4f));
        Effector other = s.hand == Effector.RIGHT_ARM ? Effector.LEFT_ARM : Effector.RIGHT_ARM;
        if (s.other != null && (!helperWanted || !free(p, other) || !SupportSearch.valid(p, s.frame, s.other, other == Effector.RIGHT_ARM))) s.other = null;
        if (helperWanted && s.other == null && free(p, other) && context.now() >= s.helperAt) {
            s.helperAt = context.now() + 200_000_000L;
            var c = s.primary.rope() != null ? AeronauticRopes.find(p, s.frame, spaces, other == Effector.RIGHT_ARM, -.1)
                    :SupportSearch.find(p, s.frame, spaces, other == Effector.RIGHT_ARM);
            if (c != null && c.world().distanceTo(s.primary.world()) > .16) s.other = c;
        }
        if (s.other != null) { offer(context, out, other, s.other); s.helper = true; }
        context.decide(s.helper ? "two-hands" : s.gap < .08 ? "held" : "reaching");
    }
    private static boolean free(AbstractClientPlayer p, Effector hand) {
        boolean main = (p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) == (hand == Effector.RIGHT_ARM);
        return p.getItemInHand(main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND).isEmpty()
                && InteractionRuntime.weight(p.getUUID(), hand, "LeadHold") < .05f;
    }
    private static void release(State s, long now) {
        if (s.primary != null) s.releaseUntil = now + 350_000_000L;
        s.primary = s.other = null;
        s.gap = s.helperGap = 1;
        s.active = false;
    }
    private static void offer(InteractionContext context, List<Candidate> out, Effector hand, SupportSearch.Contact c) {
        Vector3f point = context.frame().relativeToJoint(c.world(), new Vector3f(hand == Effector.RIGHT_ARM ? -5 : 5, 2, 0)).normalize();
        var a = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), point).getEulerAnglesZYX(new Vector3f());
        out.add(Candidate.single(INSTANCE.id(), Category.PASSIVE, 25, 1, TIMING, hand, new float[]{a.x, a.y}).withTarget(c.rope() != null ? c.rope().id :
                new strm.emfcompat.animationadditions.interaction.ContactTarget(c.space(), c.block(), context.player().level().getBlockState(c.block()).getBlock())));
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
    public static void aim(UUID uuid, Function<String, ModelPart> parts) {
        State s = STATES.fresh(uuid);
        if (s == null || s.frame == null || !s.active) return;
        s.gap = aimOne(uuid, s, s.hand, s.primary, parts);
        if (s.helper) s.helperGap = aimOne(uuid, s, s.hand == Effector.RIGHT_ARM ? Effector.LEFT_ARM : Effector.RIGHT_ARM, s.other, parts);
        if (EMFCompatConfig.getBoolean(KEY_TRACE, false) && System.nanoTime() - s.traceAt > 100_000_000L) {
            s.traceAt = System.nanoTime();
            org.slf4j.LoggerFactory.getLogger("EMFCompatTransport").info(
                "[TransportTrace] speed={} relative={} load={} owned={} gap={} helper={} helperGap={} step={} right={} left={} local={} world={} rope={} lift={}",
                s.motion.speed, s.relativeSpeed, s.load, ownership(uuid, s), s.gap, s.helper, s.helperGap,
                s.stance.stride.stepping, s.stance.stride.feet[0], s.stance.stride.feet[1], s.primary.point(), s.primary.world(), s.primary.rope() != null, s.stance.ropeLift);
        }
    }
    private static float aimOne(UUID uuid, State s, Effector hand, SupportSearch.Contact c, Function<String, ModelPart> parts) {
        if (c == null) return 1;
        ModelPart arm = parts.apply(hand.part);
        if (arm == null) return 1;
        float owned = InteractionRuntime.weight(uuid, hand, INSTANCE.id());
        if (owned < .001) return 1;
        Vector3f point = Body.model(s.frame, c.world());
        Vector3f direction = new Vector3f(point).sub(arm.x, arm.y, arm.z);
        if (direction.lengthSquared() < 1e-5) return 1;
        Quaternionf wanted = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), direction.normalize());
        Vector3f angles = new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).slerp(wanted, owned).getEulerAnglesZYX(new Vector3f());
        arm.setRotation(angles.x, angles.y, angles.z);
        Vector3f palm = Body.tip(arm, 11);
        return (float) s.frame.jointWorld(palm).distanceTo(c.world());
    }
}
