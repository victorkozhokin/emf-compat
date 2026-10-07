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
 * game draws them, and go round with them stroke by stroke; the body rocks with the stroke -
 * forward as the hands go out, back as they pull - and turns a little towards the side that
 * pulls alone. A passenger is left as the pack has them.
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

    /** Radians of lean for a pixel the hands are out from where they rest, and its limit. */
    private static final float ROCK = 0.03f, ROCK_LIMIT = 0.2f;
    /** Radians of turn for a pixel one hand is ahead of the other, and its limit. */
    private static final float TWIST = 0.022f, TWIST_LIMIT = 0.16f;
    private static final double ACTIVE_SECONDS = 0.3, REST_SECONDS = 1.5;

    private static final class State {
        float rowing, rest, pitch, yaw;
        boolean rested;
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
            state.rowing = state.pitch = state.yaw = 0;
            state.rested = false;
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

        boolean pulling = boat.getPaddleState(0) || boat.getPaddleState(1);
        double dt = context.dt();
        state.rowing += ((pulling ? 1f : 0f) - state.rowing) * Smoothing.follow(dt, ACTIVE_SECONDS);
        // Forward is -z: how far out the hands are, against where they are on the whole.
        float out0 = -(rightAt.z + leftAt.z) * 0.5f;
        if (!state.rested) {
            state.rest = out0;
            state.rested = true;
        }
        state.rest += (out0 - state.rest) * Smoothing.follow(dt, REST_SECONDS);
        float rock = Mth.clamp((out0 - state.rest) * ROCK, -ROCK_LIMIT, ROCK_LIMIT) * state.rowing;
        // The right hand ahead of the left turns the chest to the left, +yRot.
        float twist = Mth.clamp((leftAt.z - rightAt.z) * TWIST, -TWIST_LIMIT, TWIST_LIMIT) * state.rowing;
        state.pitch += (rock - state.pitch) * Smoothing.follow(dt, 0.08);
        state.yaw += (twist - state.yaw) * Smoothing.follow(dt, 0.12);

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
        if (state == null || !INSTANCE.isEnabled() || Math.abs(state.pitch) + Math.abs(state.yaw) < 1e-4f) return null;
        return TorsoLean.Hint.turn(state.pitch, state.yaw, 0f);
    }

    /** The handle of paddle {@code side} (0 or 1, as the boat counts them) in the world, as the boat is drawn. */
    static Vec3 handle(Boat boat, int side, float partial) {
        float time = boat.getRowingTime(side, partial);
        float pitch = Mth.clampedLerp(-(float) Math.PI / 3f, -0.2617994f, (Mth.sin(-time) + 1f) / 2f);
        float yaw = Mth.clampedLerp(-(float) Math.PI / 4f, (float) Math.PI / 4f, (Mth.sin(-time + 1f) + 1f) / 2f);
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
