package strm.touchnmotion.ride;

import static strm.touchnmotion.interaction.Skeleton.RIGHT_SHOULDER;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.touchnmotion.interaction.Candidate;
import strm.touchnmotion.interaction.Effector;
import strm.touchnmotion.interaction.EntityStates;
import strm.touchnmotion.interaction.InteractionContext;
import strm.touchnmotion.interaction.InteractionProvider;
import strm.touchnmotion.interaction.Skeleton;
import strm.touchnmotion.interaction.Smoothing;
import strm.touchnmotion.torso.TorsoLean;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.ik.IKFrame;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Riding in a minecart: the hands hold the cart's rim, each at a place on it an arm's length off
 * on that hand's side, and keep hold of it; the rider sits along the cart, back against its
 * back wall and legs out along its floor; and the body rides the cart as a weight on a spring rides its seat.
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

    private static final Candidate.Timing TIMING = new Candidate.Timing(0.2, 0, 0.05);
    private static final float MAX_REACH = 1.6f;
    private static final float FIST = Riders.FIST;

    /** Blocks: the middle line of the rim from the cart's middle, along it and across. */
    private static final double RIM_ALONG = 9.0 / 16, RIM_ACROSS = 7.0 / 16;
    /** The renderer lifts the cart this much, then its rim's top is this far up the cart's own upright. */
    private static final double LIFT = 0.375, RIM_OVER = 5.0 / 16 + 0.03;

    /** The legs laid out along the floor, a little apart at the feet; radians. */
    private static final float LEG_PITCH = -1.55f, LEG_SPLAY = 0.14f, LEG_ROLL = 0.04f;
    /** The rider sits this far back from where the game puts them, model pixels: back by the back wall - not so close that the back is through it as the body is thrown about - so the legs lie out flat with the feet short of the front one. */
    private static final float SIT_BACK = 4.5f;

    /** The body on its seat: how fast it comes back, a second, and how soon it settles (1 would be with no overshoot). */
    private static final double STIFF = 2 * Math.PI * 2.1, DAMP = 0.42;
    /** Blocks a second a second: a harder shove than this is no harder on the body. */
    private static final double HARDEST = 55;
    /** The share of a tick's own speed taken into the speed the shove is read from. */
    private static final double EVEN = 0.3;
    /** Radians of lean for a block the body is left behind, and as far as it leans; the bow over the knees for a block it is driven down, and its limit. */
    private static final float GIVE = 2.2f, GIVE_LIMIT = 0.34f, BOW = 3.2f, BOW_LIMIT = 0.4f;
    /** Radians: as far as it leans back and to a side - no further than keeps it inside the cart's walls. */
    private static final float BACK_LIMIT = 0.13f, SIDE_LIMIT = 0.2f;
    /** Model pixels: as far as the body comes up off the seat, and how many for each it is thrown up. */
    private static final float RISE_LIMIT = 3f, RISE = 2f;
    /** Rolling at full speed, blocks a second: the rattle of the joints - pixels up and down, radians side to side. */
    private static final float FULL_SPEED = 8f, RATTLE = 0.45f, RATTLE_ROLL = 0.02f;
    /** Seconds: the torso follows what it is asked a moment late, so it is asked that much ahead; and the body coming round when the cart turns back the way it came. */
    private static final float LEAD = 0.1f;
    private static final double FACE_SECONDS = 0.14;

    private static final class State {
        float weight, pitch, roll, rise, pitchRate, rollRate;
        int tick = -1;
        Vec3 at = Vec3.ZERO, speed = Vec3.ZERO, shove = Vec3.ZERO, went = Vec3.ZERO;
        /** The body against its seat, blocks and blocks a second: forward, to the right, up. */
        final double[] off = new double[3], rate = new double[3];
        double rolled;
        boolean riding, moving;
        /** The way the rider faces and the cart's floor's upright, in the world; and the head against the body, radians. */
        Vec3 face, floor;
        float headYaw, headPitch;
        double shoulderUp;
        /** Each hand's place on the rim: before the rider, to their right and up the cart, blocks; and in the model. */
        final Vec3[] held = new Vec3[2];
        final Vector3f[] place = {new Vector3f(), new Vector3f()};
        final float[] gap = new float[2];
        final strm.touchnmotion.DebugLog.Pace trace = new strm.touchnmotion.DebugLog.Pace();
    }

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private MinecartRide() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Ride in a minecart", true,
                "On", "In a minecart the hands hold its rim and the body rides it: back as it speeds up, out of a bend, up off the seat over a drop and down into the cart on landing.",
                "Off", "Leave the pose in a minecart to EMF.");
        config.addChild(KEY_ENABLED, KEY_LEGS, "Legs inside the cart", true,
                "On", "The rider sits back against the cart's back wall, legs out along its floor.",
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
            // Out of the cart the pose is over at once: nothing of it is carried onto the ground.
            if (state.riding) TorsoLean.drop(player.getUUID());
            state.riding = false;
            state.weight = 0;
            state.pitch = state.roll = state.rise = state.pitchRate = state.rollRate = 0;
            state.tick = -1;
            state.moving = false;
            state.face = state.floor = null;
            state.shoulderUp = 0;
            state.held[0] = state.held[1] = null;
            for (int i = 0; i < 3; i++) state.off[i] = state.rate[i] = 0;
            context.decide("off");
            return;
        }
        state.riding = true;
        state.weight += (1f - state.weight) * Smoothing.follow(dt, 0.2);
        Riders.carried(player);
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
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
            state.went = raw;
            Vec3 speed = next && state.moving ? state.speed.lerp(raw, 1.0 - Math.pow(1.0 - EVEN, ticks)) : raw;
            state.shove = next && state.moving ? speed.subtract(state.speed).scale(20.0 / ticks) : Vec3.ZERO;
            state.moving = next;
            double hard = state.shove.length();
            if (hard > HARDEST) state.shove = state.shove.scale(HARDEST / hard);
            state.speed = speed;
            state.at = at;
            state.tick = cart.tickCount;
        }
        IKFrame frame = context.frame();
        // The body's own forward, right and up, as it is drawn: the model faces -z, its right is -x, up is -y.
        Vec3 origin = frame.jointWorld(new Vector3f());
        Vec3 forward = frame.jointWorld(new Vector3f(0f, 0f, -1f)).subtract(origin).normalize();
        Vec3 right = frame.jointWorld(new Vector3f(-1f, 0f, 0f)).subtract(origin).normalize();
        Vec3 over = frame.jointWorld(new Vector3f(0f, -1f, 0f)).subtract(origin).normalize();
        double[] shove = {state.shove.dot(forward), state.shove.dot(right), state.shove.dot(over)};
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
        // Back less far than forward: behind the rider is the cart's wall.
        float give = (float) state.off[0] * GIVE;
        state.pitch = soft(give, give < 0 ? BACK_LIMIT : GIVE_LIMIT) + soft(down * BOW, BOW_LIMIT);
        state.pitchRate = (float) state.rate[0] * GIVE - (state.off[2] < 0 ? (float) state.rate[2] * BOW : 0f);
        state.roll = soft((float) state.off[1] * GIVE, SIDE_LIMIT) + rattle * RATTLE_ROLL * (float) Math.sin(state.rolled * Math.PI / 2 + 1);
        state.rollRate = (float) state.rate[1] * GIVE;
        state.rise = soft((float) Math.max(0.0, state.off[2]) * 16f / Skeleton.SCALE * RISE, RISE_LIMIT)
                + rattle * RATTLE * (float) Math.sin(state.rolled * Math.PI);

        Drawn drawn = Drawn.of(cart, partial);
        Vec3 middle = drawn.middle, along = drawn.along, across = drawn.across, up = drawn.up;
        Vec3 on = drawn.onRails ? middle : null;
        // The rider sits along the cart, facing the way it goes - at rest the way it last went, and to begin with the
        // end of it they look to - and upright to its floor; round a bend and onto a slope the body comes with the cart.
        if (state.face == null) state.face = along.scale(player.getViewVector(1f).dot(along) < 0 ? -1 : 1);
        Vec3 way = way(state, along);
        // With the cart at once - round a bend it is the cart that turns, and a body coming after it late would sit askew
        // in it. Only turned right about does it come round, by one side.
        if (state.face.dot(way) > 0) {
            state.face = way;
        } else {
            if (state.face.dot(way) < -0.95) state.face = state.face.add(across.scale(0.3)).normalize();
            state.face = state.face.lerp(way, Smoothing.follow(dt, FACE_SECONDS)).normalize();
        }
        state.floor = up;
        // Where the rider looks, against the body as it is drawn.
        Vector3f view = strm.touchnmotion.interaction.Body.model(frame, origin.add(player.getViewVector(partial)));
        float[] look = strm.touchnmotion.blockuse.aeronautics.CockpitFacing.look(view);
        state.headYaw = look[0];
        state.headPitch = look[1];
        Vec3 base = middle.add(0, LIFT, 0);
        Vec3[] grips = new Vec3[2];
        // The rider is fixed in the cart, so each hand's place on the rim is too: worked out in the cart's own measure -
        // along it, across it, up it - from where the shoulder sits, and not looked for in the world, where the cart
        // turning under a body drawn a frame behind it would send the hands hunting round the rim.
        double px = Skeleton.SCALE / 16.0, reach = FIST * px;
        // Measured the rider's way: before them, to their right, up the cart. Not the rails' way - which end of a rail
        // is "ahead" changes from one bend to the next, and a place kept in that measure would cross the cart each time.
        Vec3 before = state.face, toRight = state.face.cross(up).normalize();
        double shoulderUp = frame.jointWorld(new Vector3f(0f, RIGHT_SHOULDER.y, SIT_BACK)).subtract(base).dot(up);
        state.shoulderUp = state.shoulderUp == 0 ? shoulderUp : state.shoulderUp + (shoulderUp - state.shoulderUp) * Smoothing.follow(dt, 0.3);
        double wide = RIM_ACROSS - Math.abs(RIGHT_SHOULDER.x) * px, drop = state.shoulderUp - RIM_OVER;
        // As far before the shoulder along the side's rim as an arm reaches; an arm too short for the rim, straight out to it.
        double far = Math.min(-SIT_BACK * px + Math.sqrt(Math.max(0, reach * reach - wide * wide - drop * drop)), RIM_ALONG - 1.5 / 16);
        for (int hand = 0; hand < 2; hand++) {
            state.held[hand] = new Vec3(far, (hand == 0 ? 1 : -1) * RIM_ACROSS, RIM_OVER);
            Vec3 held = state.held[hand];
            grips[hand] = base.add(before.scale(held.x)).add(toRight.scale(held.y)).add(up.scale(held.z));
            state.place[hand].set(strm.touchnmotion.interaction.Body.model(frame, grips[hand]));
        }
        boolean held = Riders.hold(out, id(), TIMING, frame, grips[0], grips[1], MAX_REACH);
        context.decide(!held ? "out-of-reach" : on == null ? "hold:off-rails" : "hold");
    }

    /**
     * The cart as the game draws it this frame, which is not where the cart is: on rails it is drawn on the rail of
     * the block it is in, lying along that rail and tilted with a slope - on a client, between the server's word of
     * it, a cart cuts across the inside of a bend, and is drawn up to half a block from itself. Off rails it is drawn
     * where it is, turned as it is turned.
     */
    private record Drawn(Vec3 middle, Vec3 along, Vec3 across, Vec3 up, boolean onRails) {
        static Drawn of(AbstractMinecart cart, float partial) {
            Vec3 middle = cart.getPosition(partial);
            double yaw = Math.toRadians(Mth.rotLerp(partial, cart.yRotO, cart.getYRot()));
            Vec3 along = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            Vec3 on = strm.touchnmotion.platform.Platform.railPos(cart, middle.x, middle.y, middle.z, 0);
            if (on != null) {
                Vec3 ahead = strm.touchnmotion.platform.Platform.railPos(cart, middle.x, middle.y, middle.z, 0.3), behind = strm.touchnmotion.platform.Platform.railPos(cart, middle.x, middle.y, middle.z, -0.3);
                if (ahead == null) ahead = on;
                if (behind == null) behind = on;
                Vec3 lie = behind.subtract(ahead);
                if (lie.lengthSqr() > 1e-8) along = lie.normalize();
                middle = new Vec3(on.x, (ahead.y + behind.y) / 2, on.z);
            }
            Vec3 across = new Vec3(-along.z, 0, along.x).normalize();
            Vec3 up = along.cross(across);
            if (up.y < 0) up = up.scale(-1);
            return new Drawn(middle, along, across, up, on != null);
        }
    }

    /**
     * Which way along the cart the rider faces: the way they already face, carried on along the rails as they bend;
     * turned about only when the cart itself is plainly going backwards - by where it went this tick, not by its
     * evened-out speed, which through two bends one after the other still points the old way.
     */
    private static Vec3 way(State state, Vec3 along) {
        Vec3 way = along.scale(state.face.dot(along) >= 0 ? 1 : -1);
        return state.went.dot(way) < -0.5 ? way.scale(-1) : way;
    }

    /** {@code value}, but never past {@code limit} either way: the nearer the limit the less it gives. */
    private static float soft(float value, float limit) {
        return limit * (float) Math.tanh(value / limit);
    }

    /** What the ride asks of the torso; {@code null} out of a cart. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.riding || !INSTANCE.isEnabled()) return null;
        // Asked a little ahead, but never past what keeps the body inside the cart.
        return TorsoLean.Hint.turn(Mth.clamp(state.pitch + state.pitchRate * LEAD, -BACK_LIMIT, GIVE_LIMIT + BOW_LIMIT), 0f,
                Mth.clamp(state.roll + state.rollRate * LEAD, -SIDE_LIMIT, SIDE_LIMIT));
    }

    /** The legs, drawn up into the cart; before anything that works from where they are. */
    public static void legs(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || state.weight < 1e-3f || !INSTANCE.isEnabled() || !EMFCompatConfig.getBoolean(KEY_LEGS, true)) return;
        ModelPart r = parts.apply("right_leg"), l = parts.apply("left_leg");
        if (r == null || l == null) return;
        float w = state.weight;
        Riders.shift(parts, SIT_BACK * w, state.rise * w);
        // As the game seats a rider: the right leg turned out to the right is +yRot.
        r.xRot += (LEG_PITCH - r.xRot) * w;
        r.yRot += (LEG_SPLAY - r.yRot) * w;
        r.zRot += (LEG_ROLL - r.zRot) * w;
        l.xRot += (LEG_PITCH - l.xRot) * w;
        l.yRot += (-LEG_SPLAY - l.yRot) * w;
        l.zRot += (-LEG_ROLL - l.zRot) * w;
    }

    /**
     * Turns only the drawn model to sit along the cart and upright to its floor; the camera and the
     * game's own facing stay free. Before the model's space is read for anything else.
     */
    public static void orient(AbstractClientPlayer player, com.mojang.blaze3d.vertex.PoseStack stack) {
        State state = STATES.fresh(player.getUUID());
        if (state == null || !state.riding || state.face == null || !INSTANCE.isEnabled()
                || !(player.getVehicle() instanceof AbstractMinecart cart)
                || !strm.emfcompat.core.EMFCompatCore.isCompatEnabled() || strm.emfcompat.core.EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())) return;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        // Worked out for this very frame, as the cart's own drawing is: a frame late, a bend would show the rider askew.
        Drawn drawn = Drawn.of(cart, partial);
        Vec3 way = way(state, drawn.along);
        Vec3 face = state.face.dot(way) > 0 ? way : state.face;
        IKFrame frame = IKFrame.capture(stack.last().pose(), Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        Vec3 origin = frame.jointWorld(new Vector3f());
        // Into the cart as it is drawn: the rider is carried where the cart is, the cart drawn on its rail.
        Vector3f moved = strm.touchnmotion.interaction.Body.model(frame, origin.add(drawn.middle.subtract(cart.getPosition(partial))));
        stack.translate(moved.x / 16f, moved.y / 16f, moved.z / 16f);
        Vector3f toward = strm.touchnmotion.interaction.Body.model(frame, origin.add(face));
        Vector3f normal = strm.touchnmotion.interaction.Body.model(frame, origin.add(drawn.up));
        stack.mulPose(strm.touchnmotion.blockuse.aeronautics.CockpitFacing.orientation(toward, normal));
    }

    /** The last word on the hands: each fist kept on its place on the rim from where the shoulder has been thrown to. */
    public static void grip(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.riding || !INSTANCE.isEnabled()) return;
        // The body is turned to the cart; the head still looks where the rider does, as far as a neck turns.
        ModelPart head = parts.apply("head"), hat = parts.apply("hat");
        if (head != null && !strm.emfcompat.core.EMFCompatCore.isLocalPlayerInFirstPerson(uuid)
                && strm.touchnmotion.interaction.InteractionRuntime.aim(uuid, Effector.HEAD) == null) {
            head.yRot += Mth.wrapDegrees((float) Math.toDegrees(state.headYaw - head.yRot)) * Mth.DEG_TO_RAD * state.weight;
            head.xRot += (state.headPitch - head.xRot) * state.weight;
            if (hat != null) {
                hat.yRot = head.yRot;
                hat.xRot = head.xRot;
            }
        }
        Riders.grip(uuid, parts, INSTANCE.id(), state.place, state.gap);
        if (strm.touchnmotion.DebugLog.trace() && state.trace.due(30_000_000L)) {
            ModelPart body = parts.apply("body");
            org.slf4j.LoggerFactory.getLogger("EMFCompatRide").info("[CartTrace] shove=({} {} {}) off=({} {} {}) pitch={} roll={} rise={} bodyPitch={} bodyRoll={} gapR={} gapL={} heldR=({} {}) heldL=({} {})",
                    r2(state.shove.x), r2(state.shove.y), r2(state.shove.z), r2(state.off[0] * 16), r2(state.off[1] * 16), r2(state.off[2] * 16),
                    r2(Math.toDegrees(state.pitch)), r2(Math.toDegrees(state.roll)), r2(state.rise),
                    body == null ? 0 : r2(Math.toDegrees(body.xRot)), body == null ? 0 : r2(Math.toDegrees(body.zRot)), r2(state.gap[0]), r2(state.gap[1]),
                    state.held[0] == null ? 0 : r2(state.held[0].x * 16), state.held[0] == null ? 0 : r2(state.held[0].y * 16),
                    state.held[1] == null ? 0 : r2(state.held[1].x * 16), state.held[1] == null ? 0 : r2(state.held[1].y * 16));
        }
    }

    private static double r2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
