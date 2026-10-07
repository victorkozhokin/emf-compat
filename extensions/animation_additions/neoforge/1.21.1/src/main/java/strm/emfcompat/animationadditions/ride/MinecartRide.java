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
 * Riding in a minecart: the hands hold the cart's rim, each at a place on it an arm's length off
 * on that hand's side, and keep hold of it; the legs are drawn up into the cart instead of
 * standing through its front; and the body rides the cart as a weight on a spring rides its seat.
 *
 * <p>That one spring is all the motion there is. The cart's own change of speed, tick by tick,
 * pushes the seat; the body is left behind and comes after, overshooting once. So it goes back as
 * the cart picks up speed and forward as it brakes, out of a bend, up off the seat as the cart
 * drops away under it, and down into the cart - bowed over the knees - as it lands, then up again.
 * Rolling, the rail joints rattle it a little. The fists stay on the rim through all of it, so the
 * arms give as the shoulders move.</p>
 *
 * <p>The cart is taken as the game draws it: on the rails under it, lying along them, tilted with
 * a slope.</p>
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

    /** The body on its seat: how fast it comes back, a second, and how soon it settles (1 would be with no overshoot). */
    private static final double STIFF = 2 * Math.PI * 2.1, DAMP = 0.42;
    /** Blocks a second a second: a harder shove than this is no harder on the body. */
    private static final double HARDEST = 55;
    /** The share of a tick's own speed taken into the speed the shove is read from. */
    private static final double EVEN = 0.3;
    /** Radians of lean for a block the body is left behind, and as far as it leans; the bow over the knees for a block it is driven down, and its limit. */
    private static final float GIVE = 2.2f, GIVE_LIMIT = 0.34f, BOW = 3.2f, BOW_LIMIT = 0.4f;
    /** Model pixels: as far as the body comes up off the seat, and how many for each it is thrown up. */
    private static final float RISE_LIMIT = 3f, RISE = 2f;
    /** Rolling at full speed, blocks a second: the rattle of the joints - pixels up and down, radians side to side. */
    private static final float FULL_SPEED = 8f, RATTLE = 0.45f, RATTLE_ROLL = 0.02f;
    /** Seconds: the torso follows what it is asked a moment late, so it is asked that much ahead; a hand's place easing along the rim, and the body coming round after the look. */
    private static final float LEAD = 0.1f;
    private static final double SLIDE_SECONDS = 0.15, FACE_SECONDS = 0.12;

    private static final class State {
        float weight, pitch, roll, rise, pitchRate, rollRate;
        int tick = -1;
        Vec3 at = Vec3.ZERO, speed = Vec3.ZERO, shove = Vec3.ZERO;
        /** The body against its seat, blocks and blocks a second: forward, to the right, up. */
        final double[] off = new double[3], rate = new double[3];
        double rolled;
        boolean riding, moving;
        /** Each hand's place on the rim: along the cart, across it and up it, blocks; and in the model. */
        final Vec3[] held = new Vec3[2];
        final Vector3f[] place = {new Vector3f(), new Vector3f()};
        final float[] gap = new float[2];
        final strm.emfcompat.animationadditions.DebugLog.Pace trace = new strm.emfcompat.animationadditions.DebugLog.Pace();
    }

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private MinecartRide() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Ride in a minecart", true,
                "On", "In a minecart the hands hold its rim and the body rides it: back as it speeds up, out of a bend, up off the seat over a drop and down into the cart on landing.",
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
            state.pitch = state.roll = state.rise = state.pitchRate = state.rollRate = 0;
            state.tick = -1;
            state.moving = false;
            state.held[0] = state.held[1] = null;
            for (int i = 0; i < 3; i++) state.off[i] = state.rate[i] = 0;
            context.decide("off");
            return;
        }
        state.riding = true;
        state.weight += (1f - state.weight) * Smoothing.follow(dt, 0.2);
        // Carried along, a rider has no stride: the game counts one for another player all the same, and the pack bobs to it.
        player.walkAnimation.setSpeed(0f);
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        // The rider sits squarely to where they look: the game leaves the body where it was and turns only the head,
        // up to most of a quarter turn off it. The body comes round after the look instead.
        float turn = Mth.wrapDegrees(player.getYRot() - player.yBodyRot) * Smoothing.follow(dt, FACE_SECONDS);
        player.yBodyRot += turn;
        player.yBodyRotO = player.yBodyRot;

        // The shove on the seat: how the cart's speed changed this tick, blocks a second a second. From where the cart
        // was and is, not from the speed it claims - on a slope, off a drop and onto the ground that is what is felt.
        if (cart.tickCount != state.tick) {
            Vec3 at = cart.position();
            // A frame may span more than one tick.
            int ticks = cart.tickCount - state.tick;
            boolean next = state.tick >= 0 && ticks >= 1 && ticks <= 5 && at.distanceToSqr(state.at) < 9.0 * ticks * ticks;
            // The game moves a cart on a client in catches, towards where the server last said it was: its speed tick by
            // tick is ragged, and is evened out over a few ticks before its change is taken.
            Vec3 raw = next ? at.subtract(state.at).scale(20.0 / ticks) : Vec3.ZERO;
            Vec3 speed = next && state.moving ? state.speed.lerp(raw, 1.0 - Math.pow(1.0 - EVEN, ticks)) : raw;
            state.shove = next && state.moving ? speed.subtract(state.speed).scale(20.0 / ticks) : Vec3.ZERO;
            state.moving = next;
            double hard = state.shove.length();
            if (hard > HARDEST) state.shove = state.shove.scale(HARDEST / hard);
            state.speed = speed;
            state.at = at;
            state.tick = cart.tickCount;
        }
        double facing = Math.toRadians(player.yBodyRot);
        Vec3 forward = new Vec3(-Math.sin(facing), 0, Math.cos(facing)), right = new Vec3(-Math.cos(facing), 0, -Math.sin(facing));
        double[] shove = {state.shove.dot(forward), state.shove.dot(right), state.shove.y};
        // The body is left behind by the shove and drawn back to its seat; in steps short enough for the spring.
        for (double left = Math.min(dt, 0.1); left > 1e-6; left -= 1.0 / 240) {
            double step = Math.min(left, 1.0 / 240);
            for (int i = 0; i < 3; i++) {
                state.rate[i] += (-shove[i] - STIFF * STIFF * state.off[i] - 2 * DAMP * STIFF * state.rate[i]) * step;
                state.off[i] += state.rate[i] * step;
            }
        }
        double rolling = state.speed.multiply(1, 0, 1).length();
        state.rolled += rolling * dt;
        float rattle = (float) Math.min(1.0, rolling / FULL_SPEED);
        // Left behind is back (-xRot) and, pushed to the right, a lean to the left (-zRot); driven down into the seat it bows forward.
        float down = (float) Math.max(0.0, -state.off[2]);
        state.pitch = soft((float) state.off[0] * GIVE, GIVE_LIMIT) + soft(down * BOW, BOW_LIMIT);
        state.pitchRate = (float) state.rate[0] * GIVE - (state.off[2] < 0 ? (float) state.rate[2] * BOW : 0f);
        state.roll = soft((float) state.off[1] * GIVE, GIVE_LIMIT) + rattle * RATTLE_ROLL * (float) Math.sin(state.rolled * Math.PI / 2 + 1);
        state.rollRate = (float) state.rate[1] * GIVE;
        state.rise = soft((float) Math.max(0.0, state.off[2]) * 16f / Skeleton.SCALE * RISE, RISE_LIMIT)
                + rattle * RATTLE * (float) Math.sin(state.rolled * Math.PI);

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
        Vec3 base = middle.add(0, LIFT, 0);
        Vec3[] grips = new Vec3[2];
        for (int hand = 0; hand < 2; hand++) {
            // Looked for from where the shoulder is sitting still, so the hold does not wander as the body is thrown about.
            Vector3f shoulder = new Vector3f(hand == 0 ? RIGHT_SHOULDER : LEFT_SHOULDER).add(0f, 0f, SIT_BACK);
            Vec3 found = rim(frame, shoulder, hand == 0 ? -WANT_OUT : WANT_OUT, middle, along, across, up).subtract(base);
            Vec3 local = new Vec3(found.dot(along), found.dot(across), found.dot(up));
            // The rider turning, the hold goes round the rim after them; eased, and from one side to the next straight across the corner.
            state.held[hand] = state.held[hand] == null ? local : state.held[hand].lerp(local, Smoothing.follow(dt, SLIDE_SECONDS));
            Vec3 held = state.held[hand];
            grips[hand] = base.add(along.scale(held.x)).add(across.scale(held.y)).add(up.scale(held.z));
            state.place[hand].set(strm.emfcompat.animationadditions.interaction.Body.model(frame, grips[hand]));
        }
        IKResult r = OneBoneIK.solveXY(frame, RIGHT_SHOULDER, grips[0], ARM, 0f, 0f);
        IKResult l = OneBoneIK.solveXY(frame, LEFT_SHOULDER, grips[1], ARM, 0f, 0f);
        if (r != null && r.reach() > MAX_REACH) r = null;
        if (l != null && l.reach() > MAX_REACH) l = null;
        if (r != null) {
            out.add(Candidate.single(id(), Category.PASSIVE, PRIORITY, 1f, TIMING, Effector.RIGHT_ARM, new float[]{r.x(), r.y()}));
        }
        if (l != null) {
            out.add(Candidate.single(id(), Category.PASSIVE, PRIORITY, 1f, TIMING, Effector.LEFT_ARM, new float[]{l.x(), l.y()}));
        }
        context.decide(r == null && l == null ? "out-of-reach" : on == null ? "hold:off-rails" : "hold");
    }

    /** {@code value}, but never past {@code limit} either way: the nearer the limit the less it gives. */
    private static float soft(float value, float limit) {
        return limit * (float) Math.tanh(value / limit);
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
        return TorsoLean.Hint.turn(state.pitch + state.pitchRate * LEAD, 0f, state.roll + state.rollRate * LEAD);
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
            if (part == null) continue;
            part.z += SIT_BACK * w;
            // Up is -y.
            part.y -= state.rise * w;
        }
        // As the game seats a rider: the right leg turned out to the right is +yRot.
        r.xRot += (LEG_PITCH - r.xRot) * w;
        r.yRot += (LEG_SPLAY - r.yRot) * w;
        r.zRot += (LEG_ROLL - r.zRot) * w;
        l.xRot += (LEG_PITCH - l.xRot) * w;
        l.yRot += (-LEG_SPLAY - l.yRot) * w;
        l.zRot += (-LEG_ROLL - l.zRot) * w;
    }

    /** The last word on the hands: each fist kept on its place on the rim from where the shoulder has been thrown to. */
    public static void grip(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.riding || !INSTANCE.isEnabled()) return;
        for (int hand = 0; hand < 2; hand++) {
            float weight = strm.emfcompat.animationadditions.interaction.InteractionRuntime.weight(uuid, hand == 0 ? Effector.RIGHT_ARM : Effector.LEFT_ARM, INSTANCE.id());
            ModelPart arm = parts.apply(hand == 0 ? "right_arm" : "left_arm");
            if (arm != null && weight >= 0.02f) state.gap[hand] = BoatRide.settle(arm, state.place[hand], weight);
        }
        if (strm.emfcompat.animationadditions.DebugLog.trace() && state.trace.due(30_000_000L)) {
            ModelPart body = parts.apply("body");
            org.slf4j.LoggerFactory.getLogger("EMFCompatRide").info("[CartTrace] shove=({} {} {}) off=({} {} {}) pitch={} roll={} rise={} bodyPitch={} bodyRoll={} gapR={} gapL={}",
                    r2(state.shove.x), r2(state.shove.y), r2(state.shove.z), r2(state.off[0] * 16), r2(state.off[1] * 16), r2(state.off[2] * 16),
                    r2(Math.toDegrees(state.pitch)), r2(Math.toDegrees(state.roll)), r2(state.rise),
                    body == null ? 0 : r2(Math.toDegrees(body.xRot)), body == null ? 0 : r2(Math.toDegrees(body.zRot)), r2(state.gap[0]), r2(state.gap[1]));
        }
    }

    private static double r2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
