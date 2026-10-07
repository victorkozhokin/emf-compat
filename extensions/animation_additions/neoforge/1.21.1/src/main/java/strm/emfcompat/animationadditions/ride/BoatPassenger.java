package strm.emfcompat.animationadditions.ride;

import static strm.emfcompat.animationadditions.interaction.Skeleton.LEFT_SHOULDER;
import static strm.emfcompat.animationadditions.interaction.Skeleton.RIGHT_SHOULDER;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Body;
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.Category;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.animationadditions.interaction.InteractionProvider;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.interaction.Skeleton;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.animationadditions.motion.Spring;
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
 * The passenger of a rowed boat, sat in the bow facing the rower ({@link BoatSeats}): a hand on
 * each gunwale, the legs apart along the boat's sides - the rower's own are between them - and the
 * body going a little with each of the rower's strokes, towards them as they lean away.
 */
public final class BoatPassenger implements InteractionProvider {

    public static final BoatPassenger INSTANCE = new BoatPassenger();
    public static final String KEY_ENABLED = "ride.boat.passenger";

    private static final int PRIORITY = 2;
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.2, 0.2, 0.05);
    private static final float ARM = Skeleton.ARM_TO_FINGERTIPS;
    private static final float MAX_REACH = 1.7f;

    /** Radians: each leg turned out to its side, and raised a little over where the pack has it. */
    private static final float LEG_SPLAY = 0.5f, LEG_LIFT = 0.12f;
    /** The share of the rower's lean the passenger answers with, and its limit, radians. */
    private static final float ANSWER = 0.22f, ANSWER_LIMIT = 0.14f;

    private static final class State {
        final float[] along = new float[2];
        final boolean[] laid = new boolean[2];
        final Vector3f[] place = {new Vector3f(), new Vector3f()};
        final Spring pitch = new Spring();
        final float[] gap = new float[2];
        final strm.emfcompat.animationadditions.DebugLog.Pace trace = new strm.emfcompat.animationadditions.DebugLog.Pace();
        float weight;
        boolean riding;
    }

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private BoatPassenger() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addChild(BoatRide.KEY_ENABLED, KEY_ENABLED, "Passenger in the bow", true,
                "On", "The second player in a boat sits in the bow facing the rower, a hand on each gunwale, clear of the rower's stroke. A server with this addon seats them there for everyone.",
                "Off", "A passenger sits behind the rower, as the game has it - unless the server seats them in the bow.");
    }

    @Override
    public String id() {
        return "BoatPassenger";
    }

    @Override
    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(BoatRide.KEY_ENABLED, true);
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        State state = STATES.seen(player.getUUID(), context.now()).value;
        double dt = context.dt();
        if (!(player.getVehicle() instanceof Boat boat) || !BoatSeats.inBow(boat, player)) {
            state.riding = false;
            state.weight += -state.weight * Smoothing.follow(dt, 0.15);
            state.laid[0] = state.laid[1] = false;
            state.pitch.set(0);
            context.decide("off");
            return;
        }
        state.riding = true;
        state.weight += (1f - state.weight) * Smoothing.follow(dt, 0.2);
        // Carried along, a rider has no stride: the game counts one for another player all the same, and the pack bobs to it.
        player.walkAnimation.setSpeed(0f);
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        IKFrame frame = context.frame();
        // Towards the rower as they lean away: forward is +xRot.
        float answer = Math.min(ANSWER_LIMIT, BoatRide.rowerLean(boat) * ANSWER);
        state.pitch.update(answer, 0.12, dt);

        Vec3[] grips = new Vec3[2];
        for (int hand = 0; hand < 2; hand++) {
            float shoulder = hand == 0 ? RIGHT_SHOULDER.x : LEFT_SHOULDER.x;
            // The gunwale on this hand's side: the model's right is -x.
            Vector3f first = Body.model(frame, BoatRide.gunwale(new float[2], new boolean[2], 0, boat, 0, frame, new Vector3f(shoulder, 2f, 0f), partial, 0));
            int side = (first.x < 0) == (hand == 0) ? 0 : 1;
            grips[hand] = BoatRide.gunwale(state.along, state.laid, hand, boat, side, frame, BoatRide.shoulder(shoulder, -state.pitch.value, 0f), partial, dt);
            state.place[hand].set(Body.model(frame, grips[hand]));
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
        context.decide(r == null && l == null ? "out-of-reach" : "hold");
    }

    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.riding || !INSTANCE.isEnabled()) return null;
        return TorsoLean.Hint.turn(state.pitch.value + state.pitch.velocity * 0.12f, 0f, 0f);
    }

    /** The legs apart along the boat's sides; before anything that works from where they are. */
    public static void legs(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || state.weight < 1e-3f || !INSTANCE.isEnabled()) return;
        ModelPart r = parts.apply("right_leg"), l = parts.apply("left_leg");
        if (r == null || l == null) return;
        // As the game seats a rider: the right leg turned out to the right is +yRot; up is -xRot.
        r.yRot += (LEG_SPLAY - r.yRot) * state.weight;
        l.yRot += (-LEG_SPLAY - l.yRot) * state.weight;
        r.xRot -= LEG_LIFT * state.weight;
        l.xRot -= LEG_LIFT * state.weight;
    }

    /** The last word on the hands: each fist brought onto its place on the gunwale. */
    public static void grip(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.riding || !INSTANCE.isEnabled()) return;
        for (int hand = 0; hand < 2; hand++) {
            float weight = InteractionRuntime.weight(uuid, hand == 0 ? Effector.RIGHT_ARM : Effector.LEFT_ARM, INSTANCE.id());
            ModelPart arm = parts.apply(hand == 0 ? "right_arm" : "left_arm");
            if (arm != null && weight >= 0.02f) state.gap[hand] = BoatRide.settle(arm, state.place[hand], weight);
        }
        if (strm.emfcompat.animationadditions.DebugLog.trace() && state.trace.due(30_000_000L)) {
            ModelPart body = parts.apply("body"), leg = parts.apply("right_leg"), arm = parts.apply("right_arm");
            org.slf4j.LoggerFactory.getLogger("EMFCompatRide").info("[PassengerTrace] gapR={} gapL={} bodyPitch={} bodyZ={} legYaw={} armPitch={} placeR={} bodyY={} legY={} legPitch={}",
                    state.gap[0], state.gap[1], body == null ? 0 : Math.toDegrees(body.xRot), body == null ? 0 : body.z,
                    leg == null ? 0 : Math.toDegrees(leg.yRot), arm == null ? 0 : Math.toDegrees(arm.xRot), state.place[0],
                    body == null ? 0 : body.y, leg == null ? 0 : leg.y, leg == null ? 0 : Math.toDegrees(leg.xRot));
        }
    }
}
