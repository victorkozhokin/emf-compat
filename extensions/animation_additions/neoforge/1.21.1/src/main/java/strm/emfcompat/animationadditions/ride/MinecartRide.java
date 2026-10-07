package strm.emfcompat.animationadditions.ride;

import static strm.emfcompat.animationadditions.interaction.Skeleton.LEFT_SHOULDER;
import static strm.emfcompat.animationadditions.interaction.Skeleton.RIGHT_SHOULDER;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.Category;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.HandContacts;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.animationadditions.interaction.InteractionProvider;
import strm.emfcompat.animationadditions.interaction.Skeleton;
import strm.emfcompat.animationadditions.interaction.Smoothing;
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
 * Riding in a minecart: the hands hold the cart's rim, each at the place on it nearest that hand
 * whichever way the rider faces; the legs are drawn up into the cart instead of standing through
 * its front; and the body gives with the ride - back as the cart picks up speed, forward as it
 * brakes, outwards in a bend.
 *
 * <p>The cart is taken as the game draws it on level track; on a slope the rim is a little off
 * (the renderer tilts the cart from the rails under it, which is not read here).</p>
 */
public final class MinecartRide implements InteractionProvider {

    public static final MinecartRide INSTANCE = new MinecartRide();
    public static final String KEY_ENABLED = "ride.minecart", KEY_LEGS = "ride.minecart.legs";

    private static final int PRIORITY = 2;
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.2, 0.2, 0.05);
    private static final float ARM = Skeleton.ARM_TO_FINGERTIPS;
    private static final float MAX_REACH = 1.6f;

    /** Blocks: the middle line of the rim from the cart's middle, along it and across. */
    private static final double RIM_ALONG = 9.0 / 16, RIM_ACROSS = 7.0 / 16;
    /** The renderer lifts the cart this much, then its rim's top is this far up the cart's own upright. */
    private static final double LIFT = 0.375, RIM_OVER = 5.0 / 16 + 0.03;
    /** Which side a hand is of: right is -x. */
    private static final float WANT_OUT = 3f;
    /** Places tried round the rim; a multiple of four. */
    private static final int RIM_POINTS = 64;

    /** The legs drawn up: a little over level, apart at the feet; radians. */
    private static final float LEG_PITCH = -2.18f, LEG_SPLAY = 0.2f, LEG_ROLL = 0.05f;
    /** The rider sits this far back from where the game puts them, model pixels: against the back wall, the feet short of the front one. */
    private static final float SIT_BACK = 3f;

    /** Radians of lean for a block per second per second, and the limits. */
    private static final float SURGE = 0.035f, SURGE_LIMIT = 0.22f, SWAY = 0.03f, SWAY_LIMIT = 0.18f;

    private static final class State {
        float weight, pitch, roll;
        int tick = -1;
        Vec3 speed = Vec3.ZERO, gain = Vec3.ZERO;
        boolean riding;
    }

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private MinecartRide() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Ride in a minecart", true,
                "On", "In a minecart the hands hold its rim and the body gives as it speeds up, brakes and turns.",
                "Off", "Leave the pose in a minecart to EMF.");
        config.addChild(KEY_ENABLED, KEY_LEGS, "Legs inside the cart", true,
                "On", "The legs are drawn up into the cart.",
                "Off", "The legs stay as the pack has them, through the cart's front.");
    }

    @Override
    public String id() {
        return "MinecartRide";
    }

    @Override
    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        State state = STATES.seen(player.getUUID(), context.now()).value;
        double dt = context.dt();
        if (!(player.getVehicle() instanceof AbstractMinecart cart)) {
            state.riding = false;
            state.weight += -state.weight * Smoothing.follow(dt, 0.15);
            state.pitch = state.roll = 0;
            state.tick = -1;
            context.decide("off");
            return;
        }
        state.riding = true;
        state.weight += (1f - state.weight) * Smoothing.follow(dt, 0.2);
        // Carried along, a rider has no stride: the game counts one for another player all the same, and the pack bobs to it.
        player.walkAnimation.setSpeed(0f);
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);

        // What the cart's speed gained this tick, blocks a second a second, eased over a few ticks.
        if (cart.tickCount != state.tick) {
            Vec3 speed = cart.getDeltaMovement().multiply(1, 0, 1);
            if (state.tick >= 0) state.gain = state.gain.lerp(speed.subtract(state.speed).scale(20 * 20), 0.35);
            state.speed = speed;
            state.tick = cart.tickCount;
        }
        double facing = Math.toRadians(player.yBodyRot);
        Vec3 forward = new Vec3(-Math.sin(facing), 0, Math.cos(facing)), right = new Vec3(-Math.cos(facing), 0, -Math.sin(facing));
        // Thrown back as it speeds up (-xRot), out of a bend: pushed to the right the body leans left (-zRot).
        float surge = Mth.clamp((float) -state.gain.dot(forward) * SURGE, -SURGE_LIMIT, SURGE_LIMIT);
        float sway = Mth.clamp((float) -state.gain.dot(right) * SWAY, -SWAY_LIMIT, SWAY_LIMIT);
        state.pitch += (surge - state.pitch) * Smoothing.follow(dt, 0.18);
        state.roll += (sway - state.roll) * Smoothing.follow(dt, 0.18);

        IKFrame frame = context.frame();
        // As MinecartRenderer places the cart: on the rails under it, lying along them, tilted with a slope.
        Vec3 middle = cart.getPosition(partial);
        double yaw = Math.toRadians(Mth.rotLerp(partial, cart.yRotO, cart.getYRot()));
        Vec3 along = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 on = cart.getPos(middle.x, middle.y, middle.z);
        if (on != null) {
            Vec3 ahead = cart.getPosOffs(middle.x, middle.y, middle.z, 0.3), behind = cart.getPosOffs(middle.x, middle.y, middle.z, -0.3);
            if (ahead == null) ahead = on;
            if (behind == null) behind = on;
            Vec3 lie = behind.subtract(ahead);
            if (lie.lengthSqr() > 1e-8) along = lie.normalize();
            middle = new Vec3(on.x, (ahead.y + behind.y) / 2, on.z);
        }
        Vec3 across = new Vec3(-along.z, 0, along.x).normalize();
        Vec3 up = along.cross(across);
        if (up.y < 0) up = up.scale(-1);
        Vec3 rightGrip = rim(frame, RIGHT_SHOULDER, -WANT_OUT, middle, along, across, up);
        Vec3 leftGrip = rim(frame, LEFT_SHOULDER, WANT_OUT, middle, along, across, up);
        IKResult r = OneBoneIK.solveXY(frame, RIGHT_SHOULDER, rightGrip, ARM, 0f, 0f);
        IKResult l = OneBoneIK.solveXY(frame, LEFT_SHOULDER, leftGrip, ARM, 0f, 0f);
        if (r != null && r.reach() > MAX_REACH) r = null;
        if (l != null && l.reach() > MAX_REACH) l = null;
        if (r != null) {
            HandContacts.remember(context, id(), Effector.RIGHT_ARM, rightGrip);
            out.add(Candidate.single(id(), Category.PASSIVE, PRIORITY, 1f, TIMING, Effector.RIGHT_ARM, new float[]{r.x(), r.y()}));
        }
        if (l != null) {
            HandContacts.remember(context, id(), Effector.LEFT_ARM, leftGrip);
            out.add(Candidate.single(id(), Category.PASSIVE, PRIORITY, 1f, TIMING, Effector.LEFT_ARM, new float[]{l.x(), l.y()}));
        }
        context.decide(r == null && l == null ? "out-of-reach" : "hold");
    }

    /**
     * Where on the rim this hand takes hold: the place on its middle line an arm's length from the
     * shoulder - an arm does not shorten, and a hand laid on the rim right beside the body would be
     * through the wall - on the hand's own side and before the rider rather than behind.
     */
    private static Vec3 rim(IKFrame frame, Vector3f shoulder, float out, Vec3 middle, Vec3 along, Vec3 across, Vec3 up) {
        Vec3 from = frame.jointWorld(new Vector3f(shoulder));
        double length = Skeleton.ARM_TO_PALM * Skeleton.SCALE / 16;
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < RIM_POINTS; i++) {
            // Round the rectangle: the two sides, then the two ends.
            double t = (i % (RIM_POINTS / 4)) / (double) (RIM_POINTS / 4) * 2 - 1;
            int edge = i / (RIM_POINTS / 4);
            double u = edge < 2 ? t * RIM_ALONG : (edge == 2 ? RIM_ALONG : -RIM_ALONG);
            double v = edge < 2 ? (edge == 0 ? RIM_ACROSS : -RIM_ACROSS) : t * RIM_ACROSS;
            Vec3 point = middle.add(along.scale(u)).add(across.scale(v)).add(0, LIFT, 0).add(up.scale(RIM_OVER));
            Vector3f model = strm.emfcompat.animationadditions.interaction.Body.model(frame, point);
            // The model's right is -x, its front -z: a hand keeps to its side and before the body.
            float wrongSide = Math.max(0f, out < 0 ? model.x + 3f : 3f - model.x);
            float behind = Math.max(0f, model.z - 1f);
            double score = Math.abs(point.distanceTo(from) - length) * 16 + wrongSide * 0.6 + behind * 0.8;
            if (score < bestScore) {
                bestScore = score;
                best = point;
            }
        }
        return best;
    }

    /** What the ride asks of the torso; {@code null} out of a cart. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.riding || !INSTANCE.isEnabled()) return null;
        return TorsoLean.Hint.turn(state.pitch, 0f, state.roll);
    }

    /** The legs, drawn up into the cart; before anything that works from where they are. */
    public static void legs(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || state.weight < 1e-3f || !INSTANCE.isEnabled() || !EMFCompatConfig.getBoolean(KEY_LEGS, true)) return;
        ModelPart r = parts.apply("right_leg"), l = parts.apply("left_leg");
        if (r == null || l == null) return;
        float w = state.weight;
        // The model faces -z: back is +z.
        for (String name : new String[]{"body", "head", "hat", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            ModelPart part = parts.apply(name);
            if (part != null) part.z += SIT_BACK * w;
        }
        // As the game seats a rider: the right leg turned out to the right is +yRot.
        r.xRot += (LEG_PITCH - r.xRot) * w;
        r.yRot += (LEG_SPLAY - r.yRot) * w;
        r.zRot += (LEG_ROLL - r.zRot) * w;
        l.xRot += (LEG_PITCH - l.xRot) * w;
        l.yRot += (-LEG_SPLAY - l.yRot) * w;
        l.zRot += (-LEG_ROLL - l.zRot) * w;
    }
}
