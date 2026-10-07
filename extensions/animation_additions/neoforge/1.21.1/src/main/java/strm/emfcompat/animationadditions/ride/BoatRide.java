package strm.emfcompat.animationadditions.ride;

import static strm.emfcompat.animationadditions.interaction.Skeleton.LEFT_SHOULDER;
import static strm.emfcompat.animationadditions.interaction.Skeleton.RIGHT_SHOULDER;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Body;
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.Category;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.HandContacts;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.animationadditions.interaction.InteractionProvider;
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

/**
 * Rowing a boat: the hands of the one who rows are on the handles of the two paddles, where the
 * game draws them, and go round with them stroke by stroke. The body does the work. The rower
 * faces the bow and the paddles turn on the gunwales, so the stroke is a push: the blade goes back
 * through the water as the handle is driven forward. The body goes into that push - forward, and
 * further the heavier the blade - and sits back up as the blade comes round through the air.
 * Rowing on one side only that hand moves, the chest turns after it and the body leans over the
 * oar that is loaded. A passenger is left as the pack has them.
 *
 * <p>How heavy a stroke is is read off the paddle itself: how fast its handle is driven out, and
 * how deep its blade is at that moment. The torso is carried by springs, so it gathers and gives
 * up its swing instead of following the handles point for point.</p>
 *
 * <p>The paddles are the boat model's ({@code BoatModel.animatePaddle}), turned as the boat's
 * renderer turns them; they move on every client, so this shows for any player.</p>
 */
public final class BoatRide implements InteractionProvider {

    public static final BoatRide INSTANCE = new BoatRide();
    public static final String KEY_ENABLED = "ride.boat";

    /** Below anything a hand is used for: eating in a boat takes the arm. */
    private static final int PRIORITY = 2;
    private static final Candidate.Timing TIMING = new Candidate.Timing(0.18, 0.2, 0.03);
    private static final float ARM = Skeleton.ARM_TO_FINGERTIPS;
    /** As a share of the arm: a handle further than this is let go of. */
    private static final float MAX_REACH = 1.7f;

    /** Boat model pixels: where a paddle turns, and where on it the hand is - the inboard end of its shaft. */
    private static final float PIVOT_X = 3f, PIVOT_Y = -5f, RAFT_PIVOT_Y = -4f, PIVOT_Z = 9f, PADDLE_ROLL = 0.19634955f;
    private static final Vector3f HANDLE = new Vector3f(0f, 1f, -4f);

    /** Radians: the swing with the handles out and back, the set of the back while rowing, and the lean into a full push. */
    private static final float REACH = 0.17f, HUNCH = 0.04f, HAUL = 0.11f;
    /** Radians: the chest's turn after a hand all the way out, and the lean over an oar pushed alone. */
    private static final float TWIST = 0.24f, HEEL = 0.08f;
    /** A handle driven out this fast, pixels a second, is a full push. */
    private static final float FULL_PULL = 28f;
    /** Seconds: a side's rowing coming on and off, the load on it, the springs' half-life, and how far ahead they are read. */
    private static final double ACTIVE_SECONDS = 0.22, LOAD_SECONDS = 0.06, SPRING = 0.075, LEAD = 0.09;

    private static final class State {
        /** For the right hand and the left: how far it is rowing, how far out its handle is (-1..1), the load on it, and where the handle was. */
        final float[] rowing = new float[2], out = new float[2], load = new float[2], was = new float[2];
        final boolean[] seen = new boolean[2];
        final Spring pitch = new Spring(), yaw = new Spring(), roll = new Spring();
        boolean riding;
    }

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private BoatRide() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Row a boat", true,
                "On", "Rowing a boat, the hands are on the paddles' handles and the body rocks with each stroke.",
                "Off", "Leave the pose in a boat to EMF.");
    }

    @Override
    public String id() {
        return "BoatRide";
    }

    @Override
    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        State state = STATES.seen(player.getUUID(), context.now()).value;
        if (!(player.getVehicle() instanceof Boat boat) || boat.getFirstPassenger() != player) {
            if (state.riding) {
                state.riding = false;
                state.pitch.set(0);
                state.yaw.set(0);
                state.roll.set(0);
                for (int i = 0; i < 2; i++) {
                    state.rowing[i] = state.load[i] = 0;
                    state.seen[i] = false;
                }
            }
            context.decide(player.getVehicle() instanceof Boat ? "passenger" : "off");
            return;
        }
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        IKFrame frame = context.frame();
        Vec3 first = handle(boat, 0, partial), second = handle(boat, 1, partial);
        Vector3f a = Body.model(frame, first), b = Body.model(frame, second);
        // The model's right is -x.
        boolean firstRight = a.x < b.x;
        Vec3 right = firstRight ? first : second, left = firstRight ? second : first;
        Vector3f rightAt = firstRight ? a : b, leftAt = firstRight ? b : a;

        state.riding = true;
        double dt = context.dt();
        boolean pulling = false;
        for (int hand = 0; hand < 2; hand++) {
            int side = (hand == 0) == firstRight ? 0 : 1;
            Vector3f at = hand == 0 ? rightAt : leftAt;
            boolean active = boat.getPaddleState(side);
            pulling |= active;
            state.rowing[hand] += ((active ? 1f : 0f) - state.rowing[hand]) * Smoothing.follow(dt, ACTIVE_SECONDS);
            // Forward is -z. Against the middle and the ends of the handle's sweep: -1 back at the body, 1 all the way out.
            float middle = -Body.model(frame, handle(boat, side, 0f, 0.5f, partial)).z;
            float far = -Body.model(frame, handle(boat, side, 0f, 1f, partial)).z;
            float span = Math.max(1f, Math.abs(far - middle));
            float outNow = -at.z;
            state.out[hand] = Mth.clamp((outNow - middle) / span, -1f, 1f);
            // The push: the handle driven out from the body, and the deeper the blade the heavier.
            float back = state.seen[hand] && dt > 1e-4 ? (float) ((outNow - state.was[hand]) / dt) : 0f;
            state.was[hand] = outNow;
            state.seen[hand] = true;
            float time = boat.getRowingTime(side, partial);
            float deep = 1f - (Mth.sin(-time) + 1f) / 2f;
            float load = active ? Mth.clamp(back / FULL_PULL, 0f, 1f) * (0.25f + 0.75f * deep) : 0f;
            state.load[hand] += (load - state.load[hand]) * Smoothing.follow(dt, LOAD_SECONDS);
        }
        float right0 = state.out[0] * state.rowing[0], left0 = state.out[1] * state.rowing[1];
        float both = Math.max(state.rowing[0], state.rowing[1]);
        float load = (state.load[0] + state.load[1]) * 0.5f;
        // Forward is +xRot: out with the handles and into the push, back as they come home. One oar alone swings the body half as far.
        float pitch = both * HUNCH + (right0 + left0) * 0.5f * REACH + load * 2f * HAUL * (0.5f + 0.5f * Math.min(state.rowing[0], state.rowing[1]));
        // The right hand out turns the chest to the left, +yRot; a loaded oar on the right leans the body over it, +zRot.
        float yaw = (right0 - left0) * 0.5f * TWIST;
        float roll = (state.load[0] - state.load[1]) * HEEL;
        state.pitch.update(pitch, SPRING, dt);
        state.yaw.update(yaw, SPRING, dt);
        state.roll.update(roll, SPRING, dt);

        IKResult r = OneBoneIK.solveXY(frame, RIGHT_SHOULDER, right, ARM, 0f, 0f);
        IKResult l = OneBoneIK.solveXY(frame, LEFT_SHOULDER, left, ARM, 0f, 0f);
        if (r != null && r.reach() > MAX_REACH) r = null;
        if (l != null && l.reach() > MAX_REACH) l = null;
        if (r != null) {
            HandContacts.remember(context, id(), Effector.RIGHT_ARM, right);
            out.add(Candidate.single(id(), Category.PASSIVE, PRIORITY, 1f, TIMING, Effector.RIGHT_ARM, new float[]{r.x(), r.y()}));
        }
        if (l != null) {
            HandContacts.remember(context, id(), Effector.LEFT_ARM, left);
            out.add(Candidate.single(id(), Category.PASSIVE, PRIORITY, 1f, TIMING, Effector.LEFT_ARM, new float[]{l.x(), l.y()}));
        }
        context.decide(r == null && l == null ? "out-of-reach" : pulling ? "row" : "hold");
    }

    /** What the stroke asks of the torso; {@code null} out of a boat. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.riding || !INSTANCE.isEnabled()) return null;
        // Read a little ahead: the torso follows what it is asked with a lag of its own.
        float lead = (float) LEAD;
        return TorsoLean.Hint.turn(state.pitch.value + state.pitch.velocity * lead, state.yaw.value + state.yaw.velocity * lead,
                state.roll.value + state.roll.velocity * lead);
    }

    /** The handle of paddle {@code side} (0 or 1, as the boat counts them) in the world, as the boat is drawn. */
    static Vec3 handle(Boat boat, int side, float partial) {
        float time = boat.getRowingTime(side, partial);
        return handle(boat, side, (Mth.sin(-time) + 1f) / 2f, (Mth.sin(-time + 1f) + 1f) / 2f, partial);
    }

    /** The same with the paddle {@code dip} of the way up from its deepest and {@code sweep} of the way through its sweep, both 0..1. */
    private static Vec3 handle(Boat boat, int side, float dip, float sweep, float partial) {
        float pitch = Mth.clampedLerp(-(float) Math.PI / 3f, -0.2617994f, dip);
        float yaw = Mth.clampedLerp(-(float) Math.PI / 4f, (float) Math.PI / 4f, sweep);
        if (side == 1) yaw = (float) Math.PI - yaw;
        float pivotY = boat.getVariant() == Boat.Type.BAMBOO ? RAFT_PIVOT_Y : PIVOT_Y;
        // The part: its own turn (ZYX, as ModelPart applies it), then its pivot; side 1 starts turned half round.
        Vector3f point = new Quaternionf().rotationZYX(PADDLE_ROLL, yaw, pitch).transform(new Vector3f(HANDLE))
                .add(PIVOT_X, pivotY, side == 0 ? PIVOT_Z : -PIVOT_Z).div(16f);
        // The renderer: up 0.375, turned to the boat's yaw, flipped, a quarter turn.
        float boatYaw = Mth.lerp(partial, boat.yRotO, boat.getYRot());
        new Quaternionf().rotationY((float) Math.PI / 2f).transform(point);
        point.mul(-1f, -1f, 1f);
        new Quaternionf().rotationY((float) Math.toRadians(180f - boatYaw)).transform(point);
        return boat.getPosition(partial).add(point.x, point.y + 0.375, point.z);
    }
}
