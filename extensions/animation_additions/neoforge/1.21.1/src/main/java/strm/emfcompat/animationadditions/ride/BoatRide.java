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
import strm.emfcompat.animationadditions.interaction.Ease;
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
 * <p>A paddle's handle is only a few pixels from where the rower's shoulder would be sitting
 * upright - much nearer than an arm is long - so the rower sits a little back and leans back from
 * the waist by as much as puts each shoulder an arm's length from its handle: far back with the
 * handles home at the body, nearly upright with them driven out. That lean is the stroke's swing;
 * one shoulder needing to be further back than the other is the chest's turn.</p>
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

    /** Radians: the lean into a full push, over what the handles themselves ask. */
    private static final float HAUL = 0.05f;
    /** Model pixels: how far back the rower sits from where the game seats them, the torso from waist to shoulder, the arm to the middle of the fist, half the shoulders' width. */
    private static final float SIT_BACK = 3f, TORSO = 10f, GRIP = 9.5f, HALF_SHOULDERS = 5f;
    /** Model pixels: as far as a shoulder gives to bring its fist home. */
    private static final float SHRUG = 2.5f;
    /** Radians: as far back as the rower leans, and as far as the chest turns. */
    private static final float LEAN_LIMIT = 1.0f, TURN_LIMIT = 0.45f;
    /** Radians: the lean over an oar pushed alone, and the chest's turn after it. */
    private static final float HEEL = 0.08f, LONE_TURN = 0.16f;
    /** Boat model pixels: the top of a side plank, and how far along it a hand is looked for. */
    private static final float GUNWALE_Y = -3.6f, GUNWALE_Z = 9f, GUNWALE_HALF = 12f;
    /** A handle driven out this fast, pixels a second, is a full push. */
    private static final float FULL_PULL = 28f;
    /** Seconds: a side's rowing coming on and off, the load on it, the springs' half-life, and how far ahead they are read. */
    private static final double ACTIVE_SECONDS = 0.22, LOAD_SECONDS = 0.06, SPRING = 0.04, LEAD = 0.12;

    private static final class State {
        /** For the right hand and the left: how far it is rowing, how far out its handle is (-1..1), the load on it, and where the handle was. */
        final float[] rowing = new float[2], out = new float[2], load = new float[2], was = new float[2];
        final boolean[] seen = new boolean[2];
        final Spring pitch = new Spring(), yaw = new Spring(), roll = new Spring();
        boolean riding;
        float seated;
        final float[] free = new float[2];
        final float[] miss = new float[2];
        final strm.emfcompat.animationadditions.DebugLog.Pace trace = new strm.emfcompat.animationadditions.DebugLog.Pace();
        final Vector3f[] handle = {new Vector3f(), new Vector3f()};
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
                state.seated = 0;
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
        state.seated += (1f - state.seated) * Smoothing.follow(dt, 0.25);
        float both = Math.max(state.rowing[0], state.rowing[1]);
        float load = (state.load[0] + state.load[1]) * 0.5f;
        // Rowing on one side the other paddle does not move: that hand is laid on the gunwale instead, to steady the body.
        float leaning = -state.pitch.value;
        for (int hand = 0; hand < 2; hand++) {
            float free = Ease.smooth(state.rowing[1 - hand] * (1f - state.rowing[hand]));
            state.free[hand] = free;
            if (free < 1e-3f) continue;
            int side = (hand == 0) == firstRight ? 0 : 1;
            Vec3 rest = gunwale(boat, side, frame, hand == 0 ? -HALF_SHOULDERS : HALF_SHOULDERS, leaning, partial);
            if (hand == 0) {
                right = right.lerp(rest, free);
                rightAt = Body.model(frame, right);
            } else {
                left = left.lerp(rest, free);
                leftAt = Body.model(frame, left);
            }
        }
        state.handle[0].set(rightAt);
        state.handle[1].set(leftAt);
        // How far back each shoulder has to be for its hand to lie on its handle.
        float leanRight = lean(rightAt, -HALF_SHOULDERS), leanLeft = lean(leftAt, HALF_SHOULDERS);
        // Back is -xRot. The lean is the rowing hands'; a hand on the gunwale goes where the body takes it.
        float holdRight = 1f - state.free[0], holdLeft = 1f - state.free[1];
        float pitch = -(leanRight * holdRight + leanLeft * holdLeft) / Math.max(1e-3f, holdRight + holdLeft) + load * 2f * HAUL * both;
        // What one shoulder needs over the other is the chest's turn: the right shoulder further back is the chest turned to the right, -yRot.
        float apart = TORSO * (Mth.sin(leanRight) - Mth.sin(leanLeft)) * Math.min(holdRight, holdLeft);
        float yaw = Mth.clamp(-(float) Math.asin(Mth.clamp(apart / (2f * HALF_SHOULDERS), -1f, 1f)), -TURN_LIMIT, TURN_LIMIT);
        // One hand rowing alone: the chest goes a little after it as it is driven out. The right hand out is the chest turned left, +yRot.
        yaw += (state.out[0] * state.free[1] - state.out[1] * state.free[0]) * LONE_TURN;
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

    /**
     * How far back from upright, radians, the torso leans from the waist for the shoulder at
     * {@code side} (model x) to be an arm's length from {@code handle}: the shoulder goes back and
     * down as the lean grows, so the distance only grows with it and is found by halving.
     */
    private static float lean(Vector3f handle, float side) {
        float low = 0f, high = LEAN_LIMIT;
        if (reach(handle, side, high) < GRIP) return high;
        if (reach(handle, side, low) > GRIP) return low;
        for (int i = 0; i < 14; i++) {
            float mid = (low + high) * 0.5f;
            if (reach(handle, side, mid) < GRIP) low = mid;
            else high = mid;
        }
        return (low + high) * 0.5f;
    }

    /** From the shoulder to the handle with the torso leant back by {@code lean}: the waist is at y 12, back is +z. */
    private static float reach(Vector3f handle, float side, float lean) {
        float y = Skeleton.WAIST.y - TORSO * Mth.cos(lean), z = SIT_BACK + TORSO * Mth.sin(lean);
        return (float) Math.sqrt((handle.x - side) * (handle.x - side) + (handle.y - y) * (handle.y - y) + (handle.z - z) * (handle.z - z));
    }

    /** The rower, seated a little back; before anything that works from where the parts are. */
    public static void seat(UUID uuid, java.util.function.Function<String, net.minecraft.client.model.geom.ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.riding || !INSTANCE.isEnabled()) return;
        for (String name : new String[]{"body", "head", "hat", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            net.minecraft.client.model.geom.ModelPart part = parts.apply(name);
            if (part != null) part.z += SIT_BACK * state.seated;
        }
    }

    /**
     * Where on the gunwale of paddle {@code side}'s side a hand rests: the place along its top an
     * arm's length from that shoulder as the torso leans now, before the body rather than behind it.
     */
    private static Vec3 gunwale(Boat boat, int side, IKFrame frame, float shoulder, float lean, float partial) {
        float y = Skeleton.WAIST.y - TORSO * Mth.cos(lean), z = SIT_BACK + TORSO * Mth.sin(lean);
        Vec3 best = null;
        float bestScore = Float.MAX_VALUE;
        for (float along = -GUNWALE_HALF; along <= GUNWALE_HALF; along += 0.5f) {
            Vec3 point = world(boat, new Vector3f(along, GUNWALE_Y, side == 0 ? GUNWALE_Z : -GUNWALE_Z), partial);
            Vector3f model = Body.model(frame, point);
            float reach = (float) Math.sqrt((model.x - shoulder) * (model.x - shoulder) + (model.y - y) * (model.y - y) + (model.z - z) * (model.z - z));
            // Forward is -z: a place behind the shoulder is a last resort.
            float score = Math.abs(reach - GRIP) + Math.max(0f, model.z - (z - 3f)) * 0.7f;
            if (score < bestScore) {
                bestScore = score;
                best = point;
            }
        }
        return best;
    }

    /**
     * The last word on a hand that rows or rests: the arm is turned onto its place from where the
     * shoulder has ended up, and what is still left between fist and place - the torso follows its
     * lean a moment late - is taken up at the shoulder. After everything else has posed the arms.
     */
    public static void grip(UUID uuid, java.util.function.Function<String, net.minecraft.client.model.geom.ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.riding || !INSTANCE.isEnabled()) return;
        for (int hand = 0; hand < 2; hand++) {
            Effector effector = hand == 0 ? Effector.RIGHT_ARM : Effector.LEFT_ARM;
            float weight = strm.emfcompat.animationadditions.interaction.InteractionRuntime.weight(uuid, effector, INSTANCE.id());
            net.minecraft.client.model.geom.ModelPart arm = parts.apply(hand == 0 ? "right_arm" : "left_arm");
            if (arm == null || weight < 0.02f) continue;
            strm.emfcompat.animationadditions.interaction.ArmAim.towards(arm, state.handle[hand], weight, true);
            Vector3f left = new Vector3f(state.handle[hand]).sub(Body.tip(arm, GRIP));
            if (left.length() > SHRUG) left.normalize(SHRUG);
            arm.x += left.x * weight;
            arm.y += left.y * weight;
            arm.z += left.z * weight;
            state.miss[hand] = Body.tip(arm, GRIP).distance(state.handle[hand]);
        }
        if (strm.emfcompat.animationadditions.DebugLog.trace() && state.trace.due(50_000_000L)) {
            net.minecraft.client.model.geom.ModelPart body = parts.apply("body");
            org.slf4j.LoggerFactory.getLogger("EMFCompatRide").info("[BoatTrace] missR={} missL={} freeR={} freeL={} pitch={} yaw={}",
                    state.miss[0], state.miss[1], state.free[0], state.free[1],
                    body == null ? 0 : Math.toDegrees(body.xRot), body == null ? 0 : Math.toDegrees(body.yRot));
        }
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
        return world(boat, new Quaternionf().rotationZYX(PADDLE_ROLL, yaw, pitch).transform(new Vector3f(HANDLE))
                .add(PIVOT_X, pivotY, side == 0 ? PIVOT_Z : -PIVOT_Z), partial);
    }

    /** A point of the boat's model, pixels, in the world as the boat's renderer draws it: up 0.375, turned to the boat's yaw, flipped, a quarter turn. */
    private static Vec3 world(Boat boat, Vector3f point, float partial) {
        point.div(16f);
        float boatYaw = Mth.lerp(partial, boat.yRotO, boat.getYRot());
        new Quaternionf().rotationY((float) Math.PI / 2f).transform(point);
        point.mul(-1f, -1f, 1f);
        new Quaternionf().rotationY((float) Math.toRadians(180f - boatYaw)).transform(point);
        return boat.getPosition(partial).add(point.x, point.y + 0.375, point.z);
    }
}
