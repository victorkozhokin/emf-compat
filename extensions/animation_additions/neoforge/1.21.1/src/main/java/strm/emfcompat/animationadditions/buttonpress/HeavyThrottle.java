package strm.emfcompat.animationadditions.buttonpress;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.*;
import strm.emfcompat.animationadditions.torso.LowReach;
import strm.emfcompat.core.*;
import strm.emfcompat.core.ik.*;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import strm.emfcompat.animationadditions.interaction.Ease;
import strm.emfcompat.animationadditions.interaction.Body;

/** A held Throttle receives phased visual effort; cockpit and item use retain their hands. */
public final class HeavyThrottle implements InteractionProvider {
    public static final HeavyThrottle INSTANCE = new HeavyThrottle();
    public static final String KEY = "buttonpress.heavy_throttle";
    private static final EntityStates<State> STATES = new EntityStates<>(State::new);
    private static final Candidate.Timing TIMING = new Candidate.Timing(.14, .2, .06);
    private static final class State {
        final ThrottleEffort motion = new ThrottleEffort();
        final LeverStep step = new LeverStep();
        final LowReach.State reach = new LowReach.State();
        final Vector3f right = new Vector3f(), left = new Vector3f(), centre = new Vector3f(), axis = new Vector3f(0, 0, -1);
        AbstractClientPlayer player;
        IKFrame frame;
        BlockPos pos;
        Vec3 previousKnob;
        Effector primary = Effector.RIGHT_ARM;
        boolean active, helper, stepped;
        long stepRequest, traceAt;
        int signal;
        float primaryGap = Float.POSITIVE_INFINITY, helperGap = Float.POSITIVE_INFINITY;
    }
    public String id() { return "HeavyThrottle"; }
    public boolean isEnabled() { return ButtonPress.INSTANCE.isEnabled() && EMFCompatConfig.getBoolean(KEY, true); }
    public static void register(ConfigRegistry.Group config) {
        config.addChild(ButtonPress.KEY_ENABLED, KEY, "Throttle effort", true,
            "On", "Brace and transfer weight while dragging a Throttle; a free hand helps after a long stroke.",
            "Off", "Use the ordinary single-hand Throttle pose.");
    }
    public void collect(InteractionContext context, List<Candidate> out) {
        var player = context.player();
        State s = STATES.seen(player.getUUID(), context.now()).value;
        s.active = false;
        s.helper = false;
        s.player = player;
        s.frame = context.frame();
        BlockPos pos = player == Minecraft.getInstance().player ? ThrottleLever.heldPosition() : strm.emfcompat.animationadditions.net.Inputs.throttle(player);
        boolean eligible = pos != null && player.onGround() && !Seated.seated(player) && !player.isPassenger()
            && !player.isUsingItem() && !player.isSleeping() && !player.isInWaterOrBubble()
            && !player.isFallFlying() && player.getDeltaMovement().horizontalDistanceSqr() < .0025;
        Integer signal = eligible ? ThrottleLever.signal(player.level(), pos) : null;
        Vec3[] grips = signal == null ? null : ThrottleLever.grips(player.level(), pos);
        boolean fresh = pos != null && !pos.equals(s.pos);
        float helperWeight = InteractionRuntime.weight(player.getUUID(), s.primary == Effector.RIGHT_ARM ? Effector.LEFT_ARM : Effector.RIGHT_ARM, id());
        boolean helperReady = helperWeight > .95f && s.helperGap < .07f
            && InteractionRuntime.weight(player.getUUID(), s.primary, id()) > .95f;
        s.motion.advance(signal, grips != null, helperReady, context.dt(), fresh);
        if (grips == null) {
            s.pos = null;
            s.step.observe(player, context.frame(), false, s.centre, s.stepRequest);
            context.decide("off:hold");
            return;
        }
        Vec3 knob = grips[0].add(grips[1]).scale(.5);
        Vector3f a = Body.model(context.frame(), SubLevels.toWorld(player.level(), pos, grips[0]));
        Vector3f b = Body.model(context.frame(), SubLevels.toWorld(player.level(), pos, grips[1]));
        s.right.set(a.x <= b.x ? a : b);
        s.left.set(a.x <= b.x ? b : a);
        s.centre.set(s.right).add(s.left).mul(.5f);
        if (s.centre.z > 2 || s.centre.lengthSquared() > 28 * 28) {
            s.pos = null;
            s.step.observe(player, context.frame(), false, s.centre, s.stepRequest);
            context.decide("off:reach");
            return;
        }
        if (fresh) {
            s.primary = s.centre.x <= 0 ? Effector.RIGHT_ARM : Effector.LEFT_ARM;
            s.previousKnob = knob;
            s.axis.set(0, 0, -1);
            s.stepped = false;
            s.primaryGap = s.helperGap = Float.POSITIVE_INFINITY;
        } else if (s.previousKnob != null && signal != s.signal) {
            Vec3 delta = knob.subtract(s.previousKnob).scale(Math.signum(signal - s.signal));
            if (delta.lengthSqr() > 1e-8) {
                Vector3f tip = Body.model(context.frame(), SubLevels.toWorld(player.level(), pos, knob.add(delta)));
                Vector3f axis = tip.sub(s.centre);
                axis.y = 0;
                if (axis.lengthSquared() > 1e-5) s.axis.lerp(axis.normalize(), Smoothing.follow(context.dt(), .1));
            }
        }
        s.previousKnob = knob;
        s.signal = signal;
        s.pos = pos.immutable();
        Effector other = s.primary == Effector.RIGHT_ARM ? Effector.LEFT_ARM : Effector.RIGHT_ARM;
        Vector3f helperPoint = other == Effector.RIGHT_ARM ? s.right : s.left;
        Vector3f helperShoulder = new Vector3f(other == Effector.RIGHT_ARM ? -5 : 5, 2, 0);
        s.helper = s.motion.assist > .06f && helperPoint.distance(helperShoulder) < 14
            && player.getItemInHand(other == Effector.RIGHT_ARM == (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT)
                ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND).isEmpty();
        if (!s.helper || !helperReady) s.motion.regrip = 0;
        Vector3f main = s.primary == Effector.RIGHT_ARM ? s.right : s.left;
        // Tiny loss-and-recovery of the primary grip only while the helper is planted.
        main.add(0, s.motion.regrip * .55f, s.motion.regrip * 1.2f);
        offer(context, out, s.primary, main, 1);
        if (s.helper) offer(context, out, other, helperPoint, s.motion.assist);
        s.active = true;
        if (!s.stepped && s.motion.load > .25f) { s.stepped = true; s.stepRequest = context.now(); }
        s.step.observe(player, context.frame(), true, s.centre, s.stepRequest);
        context.decide(s.motion.regrip > .05f ? "regrip" : s.helper ? "two-hand" : s.motion.load > .1f ? "effort" : "hold");
    }
    private void offer(InteractionContext context, List<Candidate> out, Effector hand, Vector3f point, float confidence) {
        Vector3f shoulder = new Vector3f(hand == Effector.RIGHT_ARM ? -5 : 5, 2, 0);
        Vec3 target = context.frame().jointWorld(point);
        var aim = OneBoneIK.solveXY(context.frame(), shoulder, target, 11, 0, 0);
        if (aim == null) return;
        State state = STATES.fresh(context.player().getUUID());
        var space = SubLevels.at(context.player().level(), state.pos);
        out.add(Candidate.single(id(), Category.USE, 12, confidence, TIMING, hand, new float[]{aim.x(), aim.y()}).withTarget(
                new strm.emfcompat.animationadditions.interaction.ContactTarget(space, state.pos, context.player().level().getBlockState(state.pos).getBlock())));
        HandContacts.remember(context, id(), hand, target, space);
    }
    public static float[] torsoHint(UUID uuid) {
        State s = STATES.fresh(uuid);
        if (s == null || !s.active || !INSTANCE.isEnabled()) return null;
        float owned = InteractionRuntime.weight(uuid, s.primary, INSTANCE.id());
        float drive = s.motion.direction * s.motion.load * .10f + s.motion.recoil * .07f;
        return new float[]{-s.axis.z * drive * owned, 0, s.axis.x * drive * .65f * owned};
    }
    /** Grounded fit and short approach step, before the final arm contact. */
    public static void reachContact(UUID uuid, Function<String, ModelPart> parts) {
        State s = STATES.fresh(uuid);
        if (s == null || s.player == null) return;
        float owned = s.active && INSTANCE.isEnabled() ? InteractionRuntime.weight(uuid, s.primary, INSTANCE.id()) : 0;
        s.step.stride = 1.6f;
        s.step.duration = .32f;
        s.step.height = .5f;
        s.step.apply(parts, owned);
        s.reach.weightShift = s.axis.x * (s.motion.direction * s.motion.load * .85f + s.motion.recoil * .4f);
        s.reach.weightForward = s.axis.z * (s.motion.direction * s.motion.load * .85f + s.motion.recoil * .4f);
        boolean right = s.primary == Effector.RIGHT_ARM;
        Vector3f primary = right ? s.right : s.left;
        // Once the arm is fully extended, effort may not shift its shoulder farther
        // away from the contact. Contact takes priority over extra weight travel.
        ModelPart arm = parts.apply(right ? "right_arm" : "left_arm");
        if (arm != null) {
            float distance = primary.distance(new Vector3f(arm.x, arm.y, arm.z));
            float far = Ease.smooth((distance - 11) / 6);
            // A near knob must not make a rigid arm fit fold the whole torso
            // sideways by forty degrees just to shorten its reach.
            s.reach.angleLimit = (float) Math.toRadians(12 + 28 * far);
            Vector3f to = new Vector3f(primary).sub(arm.x, arm.y, arm.z);
            if (to.length() > 10.5f) {
                float away = s.reach.weightShift * to.x + s.reach.weightForward * to.z;
                float horizontal = to.x * to.x + to.z * to.z;
                if (away < 0 && horizontal > 1e-5f) {
                    s.reach.weightShift -= away * to.x / horizontal;
                    s.reach.weightForward -= away * to.z / horizontal;
                }
            }
        }

        Effector helper = right ? Effector.LEFT_ARM : Effector.RIGHT_ARM;
        Vector3f other = s.helper && InteractionRuntime.weight(uuid, helper, INSTANCE.id()) > .5f ? right ? s.left : s.right : null;
        s.reach.followSeconds = .24;
        LowReach.apply(parts, right, primary, other, owned, s.reach);
    }
    public static void aimArms(UUID uuid, Function<String, ModelPart> parts) {
        State s = STATES.fresh(uuid);
        if (s == null) return;
        for (Effector hand : new Effector[]{Effector.RIGHT_ARM, Effector.LEFT_ARM}) {
            float owned = InteractionRuntime.weight(uuid, hand, INSTANCE.id());
            ModelPart arm = parts.apply(hand.part);
            if (owned < 1e-3f || arm == null) continue;
            Vector3f point = hand == Effector.RIGHT_ARM ? s.right : s.left;
            Vector3f to = new Vector3f(point).sub(arm.x, arm.y, arm.z);
            if (to.lengthSquared() < 1e-6) continue;
            to.normalize();
            arm.xRot += IKMath.wrap(-(float) Math.acos(Mth.clamp(to.y, -1, 1)) - arm.xRot) * owned;
            arm.yRot += IKMath.wrap((float) Math.atan2(-to.x, -to.z) - arm.yRot) * owned;
            arm.zRot *= 1 - owned;
        }
        s.primaryGap = gap(parts.apply(s.primary.part), s.primary == Effector.RIGHT_ARM ? s.right : s.left);
        s.helperGap = gap(parts.apply(s.primary == Effector.RIGHT_ARM ? "left_arm" : "right_arm"),
            s.primary == Effector.RIGHT_ARM ? s.left : s.right);
        if (strm.emfcompat.animationadditions.DebugLog.trace()
                && System.nanoTime() - s.traceAt > 60_000_000L) {
            s.traceAt = System.nanoTime();
            org.slf4j.LoggerFactory.getLogger("EMFCompatThrottle").info(
                "[ThrottleEffort] signal={} load={} assist={} recoil={} regrip={} primaryRight={} primaryWeight={} helperWeight={} primaryGap={} helperGap={} step={} progress={} right={} left={}",
                s.signal, s.motion.load, s.motion.assist, s.motion.recoil, s.motion.regrip, s.primary == Effector.RIGHT_ARM,
                InteractionRuntime.weight(uuid, s.primary, INSTANCE.id()),
                InteractionRuntime.weight(uuid, s.primary == Effector.RIGHT_ARM ? Effector.LEFT_ARM : Effector.RIGHT_ARM, INSTANCE.id()),
                s.primaryGap, s.helperGap, s.step.consumed, s.step.progress, s.right, s.left);
        }
    }
    private static float gap(ModelPart arm, Vector3f point) {
        if (arm == null) return Float.POSITIVE_INFINITY;
        return Body.tip(arm, 11).distance(point) / 16;
    }
}
