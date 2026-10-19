package strm.touchnmotion.ride;

import static strm.touchnmotion.interaction.Skeleton.RIGHT_SHOULDER;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.ik.IKFrame;
import strm.touchnmotion.horsesync.HorseSync;
import strm.touchnmotion.interaction.Body;
import strm.touchnmotion.interaction.Candidate;
import strm.touchnmotion.interaction.Effector;
import strm.touchnmotion.interaction.EntityStates;
import strm.touchnmotion.interaction.InteractionContext;
import strm.touchnmotion.interaction.InteractionProvider;
import strm.touchnmotion.interaction.InteractionRuntime;
import strm.touchnmotion.interaction.Skeleton;
import strm.touchnmotion.interaction.Smoothing;
import strm.touchnmotion.torso.TorsoLean;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Riding a horse - any horse, donkey, mule or camel the game seats a rider on: the hands hold the
 * reins over the withers, each a hand's width off the neck on its own side, in the horse's own
 * measure, so they stay with it as it turns and go after its head as it grazes or rears; the legs
 * grip its sides; the torso leans over the neck as a jockey's, if asked; the head looks where the
 * rider looks.
 *
 * <p>A hand the rider uses - eating, a shield, a swing - lets go of the reins and the other keeps
 * hold; while one is off the rider sits up, so the arm the game and the pack pose at a standing
 * shoulder stays on it. Both go for a bow or a crossbow. Another mod's pose of one arm takes that
 * arm only.</p>
 */
public final class HorseRide implements InteractionProvider {

    public static final HorseRide INSTANCE = new HorseRide();

    private static final Candidate.Timing TIMING = new Candidate.Timing(0.2, 0.15, 0.05);
    private static final float MAX_REACH = 1.6f;
    private static final float FIST = Riders.FIST;

    /** Blocks, from the waist, along the horse, to its side and up: where the hands hold the reins, the nearest and furthest forward. */
    private static final double GRIP_UP = 0.12, GRIP_WIDE = 0.15, GRIP_NEAR = 0.22, GRIP_AT = 0.6;
    /** Blocks the hands go on along the neck for each radian the horse's nose is up: over a jump the rider gives it the reins. */
    private static final double RELEASE = 0.5;
    /** Blocks: the hands going after the head as the horse grazes (forward, down) and as it rears (up). */
    private static final double GRAZE_FORWARD = 0.1, GRAZE_DOWN = 0.08, REAR_UP = 0.2;
    /** Radians: the jockey's lean over the neck; and the legs gripping the sides. */
    private static final float JOCKEY = 0.55f, LEG_ROLL = 0.5f, LEG_X = 2.9f;
    /** Model pixels: the rider sits this far back from where the game seats them - in the middle of the saddle, not on the withers. */
    private static final float SIT_BACK = 0f;
    /** Seconds the rider takes to sit up as a hand leaves the reins, and to lean in again. */
    private static final double SIT_UP_SECONDS = 0.08;
    private static final int RIGHT = 1, LEFT = 2, TORSO = 4;
    /** Of the saddle's turn nose up or down, the share the torso takes back: the rider stays more level than the horse. */
    private static final float COUNTER = 0.85f;
    /** The body on the saddle: how fast it comes back, a second, and how soon it settles (1 would be with no overshoot). */
    private static final double STIFF = 2 * Math.PI * 1.8, DAMP = 0.5;
    /** Blocks a second a second: a harder shove than this is no harder on the body; the share of a tick's speed taken in. */
    private static final double HARDEST = 40, EVEN = 0.3;
    /** Radians of lean for a block the body is left behind; the bow over the neck for a block it is driven down. */
    private static final float GIVE = 2.0f, BOW = 2.5f;
    /** Radians: as far as it leans forward, back, to a side and bows; model pixels it comes up off the saddle, per block thrown and at most. */
    private static final float FORWARD_LIMIT = 0.3f, BACK_LIMIT = 0.18f, SIDE_LIMIT = 0.15f, BOW_LIMIT = 0.3f, RISE = 1.5f, RISE_LIMIT = 1.5f;
    /** Seconds: the torso follows what it is asked a moment late, so it is asked that much ahead. */
    private static final float LEAD = 0.1f;
    /** Radians: as far over the neck as the torso goes, all of it together - further, the shoulders drop below the reins. */
    private static final float MOST_LEAN = 0.95f;

    private static final class State {
        float weight, upright;
        boolean riding;
        int busy = -1;
        float headYaw, headPitch;
        final Vector3f[] place = {new Vector3f(), new Vector3f()};
        final float[] gap = new float[2];
        /** The horse a tick ago and its speed, blocks and blocks a second; the shove on the saddle, blocks a second a second. */
        int tick = -1;
        boolean moving;
        Vec3 at = Vec3.ZERO, speed = Vec3.ZERO, shove = Vec3.ZERO;
        /** The body against its saddle, blocks and blocks a second: forward, to the right, up. */
        final double[] off = new double[3], rate = new double[3];
        float pitch, roll, rise, pitchRate, rollRate, noseUp;
        final strm.touchnmotion.DebugLog.Pace trace = new strm.touchnmotion.DebugLog.Pace();
    }

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private HorseRide() {
    }

    @Override
    public String id() {
        return "HorseRide";
    }

    @Override
    public boolean isEnabled() {
        return HorseSync.isEnabled() && HorseSync.isRidingAnimation();
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        State state = STATES.seen(player.getUUID(), context.now()).value;
        double dt = context.dt();
        if (!(player.getVehicle() instanceof AbstractHorse horse)) {
            // Off the horse the pose is over at once: nothing of it is carried onto the ground.
            if (state.riding) TorsoLean.drop(player.getUUID());
            state.riding = false;
            state.weight = state.upright = 0;
            state.busy = -1;
            state.tick = -1;
            state.moving = false;
            state.pitch = state.roll = state.rise = state.pitchRate = state.rollRate = state.noseUp = 0;
            for (int i = 0; i < 3; i++) state.off[i] = state.rate[i] = 0;
            context.decide("off");
            return;
        }
        state.riding = true;
        state.weight += (1f - state.weight) * Smoothing.follow(dt, 0.2);
        Riders.carried(player);
        int busy = busy(player);
        state.upright += ((busy != 0 ? 1 : 0) - state.upright) * Smoothing.follow(dt, SIT_UP_SECONDS);
        state.busy = busy;

        float partial = strm.touchnmotion.platform.Platform.partialTick(false);
        IKFrame frame = context.frame();
        // The horse's own measure: along its body as it is drawn, to its right, and up.
        double yaw = Math.toRadians(Mth.rotLerp(partial, horse.yBodyRotO, horse.yBodyRot));
        Vec3 level = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        ride(state, horse, level, right, dt);
        // Along the saddle as the pack turns the horse's body, nose up or down.
        state.noseUp = strm.touchnmotion.horsesync.ClientEventHandler.noseUp(player);
        double c = Math.cos(state.noseUp), sn = Math.sin(state.noseUp);
        Vec3 along = level.scale(c).add(0, sn, 0), up = new Vec3(0, c, 0).subtract(level.scale(sn));
        Vec3 waist = frame.jointWorld(new Vector3f(Skeleton.WAIST));
        Vec3 origin = frame.jointWorld(new Vector3f());
        // Where the rider looks, against the body as it is drawn.
        float[] look = strm.touchnmotion.seat.CockpitFacing.look(Body.model(frame, origin.add(player.getViewVector(partial))));
        state.headYaw = look[0];
        state.headPitch = look[1];

        // Each hand as far before its shoulder as an arm reaches, the shoulder where the lean will have put it; never
        // closer to the rider than the saddle's front, nor further than the withers.
        float lean = -Math.min(MOST_LEAN, jockey(state) + COUNTER * state.noseUp + state.pitch);
        double px = Skeleton.SCALE / 16.0, reach = FIST * px;
        float graze = horse.getEatAnim(partial), rear = horse.getStandAnim(partial);
        Vec3[] grips = new Vec3[2];
        for (int hand = 0; hand < 2; hand++) {
            float side = hand == 0 ? RIGHT_SHOULDER.x : -RIGHT_SHOULDER.x;
            Vec3 shoulder = frame.jointWorld(Riders.shoulder(side, lean, SIT_BACK * state.weight));
            Vec3 base = waist.add(up.scale(GRIP_UP)).add(right.scale((hand == 0 ? 1 : -1) * GRIP_WIDE));
            Vec3 d = base.subtract(shoulder);
            double ahead = d.dot(along);
            double far = -ahead + Math.sqrt(Math.max(0, ahead * ahead - d.lengthSqr() + reach * reach));
            // The reins are where they are, over the withers: the hands reach for them from the middle of the saddle,
            // and an arm too short - the rider sat up - goes as far towards them as it reaches.
            // Over a jump, folded over the neck, the rider gives the horse its head: the hands go on along it.
            far = Mth.clamp(far, GRIP_NEAR, GRIP_AT + RELEASE * Math.max(0, state.noseUp));
            grips[hand] = base.add(along.scale(far + graze * GRAZE_FORWARD)).add(up.scale(rear * REAR_UP - graze * GRAZE_DOWN));
            state.place[hand].set(Body.model(frame, grips[hand]));
        }
        boolean held = Riders.hold(out, id(), TIMING, frame, grips[0], grips[1], MAX_REACH);
        context.decide(!held ? "out-of-reach" : busy == 0 ? "reins" : busy == RIGHT ? "reins:left" : busy == LEFT ? "reins:right" : "reins:free");
    }

    /**
     * The body on the saddle as a weight on a spring: the horse's own change of speed, tick by tick, shoves the
     * saddle and the body is left behind and comes after, overshooting once - back as the horse sets off, forward
     * as it stops, out of a bend, down onto the neck as it lands and up off the saddle as it springs.
     */
    private static void ride(State state, AbstractHorse horse, Vec3 along, Vec3 right, double dt) {
        if (horse.tickCount != state.tick) {
            Vec3 at = horse.position();
            int ticks = horse.tickCount - state.tick;
            boolean next = state.tick >= 0 && ticks >= 1 && ticks <= 5 && at.distanceToSqr(state.at) < 9.0 * ticks * ticks;
            // From where the horse was and is: on a client it moves in catches, so its speed is evened over a few ticks.
            Vec3 raw = next ? at.subtract(state.at).scale(20.0 / ticks) : Vec3.ZERO;
            Vec3 speed = next && state.moving ? state.speed.lerp(raw, 1.0 - Math.pow(1.0 - EVEN, ticks)) : raw;
            state.shove = next && state.moving ? speed.subtract(state.speed).scale(20.0 / ticks) : Vec3.ZERO;
            state.moving = next;
            double hard = state.shove.length();
            if (hard > HARDEST) state.shove = state.shove.scale(HARDEST / hard);
            state.speed = speed;
            state.at = at;
            state.tick = horse.tickCount;
        }
        double[] shove = {state.shove.dot(along), state.shove.dot(right), state.shove.y};
        for (double left = Math.min(dt, 0.1); left > 1e-6; left -= 1.0 / 240) {
            double step = Math.min(left, 1.0 / 240);
            for (int i = 0; i < 3; i++) {
                state.rate[i] += (-shove[i] - STIFF * STIFF * state.off[i] - 2 * DAMP * STIFF * state.rate[i]) * step;
                state.off[i] += state.rate[i] * step;
            }
        }
        // Left behind is a lean back (-xRot), pushed right a lean left (-zRot); driven down it bows forward.
        float give = (float) state.off[0] * GIVE, down = (float) Math.max(0.0, -state.off[2]);
        state.pitch = soft(give, give < 0 ? BACK_LIMIT : FORWARD_LIMIT) + soft(down * BOW, BOW_LIMIT);
        state.pitchRate = (float) state.rate[0] * GIVE - (state.off[2] < 0 ? (float) state.rate[2] * BOW : 0f);
        state.roll = soft((float) state.off[1] * GIVE, SIDE_LIMIT);
        state.rollRate = (float) state.rate[1] * GIVE;
        state.rise = soft((float) Math.max(0.0, state.off[2]) * 16f / Skeleton.SCALE * RISE, RISE_LIMIT);
    }

    /** {@code value}, but never past {@code limit} either way: the nearer the limit the less it gives. */
    private static float soft(float value, float limit) {
        return limit * (float) Math.tanh(value / limit);
    }

    /** The jockey's lean over the neck now, radians forward: none sat up, or with the option off. */
    private static float jockey(State state) {
        if (!EMFCompatConfig.getBoolean(HorseSync.KEY_JOCKEY, true)) return 0f;
        return JOCKEY * state.weight * (1f - state.upright);
    }

    /**
     * The arms the reins cannot have, on a horse; {@code null} off one. The runtime leaves a used arm to
     * the game: here only the one in use, not both.
     */
    public static Set<Effector> reserved(AbstractClientPlayer player) {
        if (!INSTANCE.isEnabled() || !(player.getVehicle() instanceof AbstractHorse)) return null;
        int busy = busy(player);
        Set<Effector> reserved = EnumSet.noneOf(Effector.class);
        if ((busy & (RIGHT | TORSO)) != 0) reserved.add(Effector.RIGHT_ARM);
        if ((busy & (LEFT | TORSO)) != 0) reserved.add(Effector.LEFT_ARM);
        return reserved;
    }

    /** The arms something else is using: an item in use, a loaded crossbow held, a swing, another mod's pose. */
    private static int busy(AbstractClientPlayer player) {
        int busy = 0;
        if (player.isUsingItem()) {
            String use = player.getUseItem().getUseAnimation().name();
            busy |= "BOW".equals(use) || "CROSSBOW".equals(use) ? RIGHT | LEFT : arm(player, player.getUsedItemHand());
        }
        // A loaded crossbow is held up in both hands, as the game draws it.
        if (charged(player.getMainHandItem()) || charged(player.getOffhandItem())) busy |= RIGHT | LEFT;
        if (strm.touchnmotion.platform.Platform.swinging(player)) busy |= arm(player, strm.touchnmotion.platform.Platform.swingingArm(player));
        busy |= others(player.getUUID());
        if (strm.touchnmotion.compat.BodyClaims.arms(player.getUUID())) busy |= RIGHT | LEFT;
        return busy;
    }

    /** What other mods' poses hold: either arm, and the torso or head (then both arms with it). */
    private static int others(UUID uuid) {
        if (!strm.emfcompat.core.EMFCompatCore.isCompatEnabled()) return 0;
        int held = held(PoseManager.entitySavedPoses.get(uuid));
        var sources = PoseManager.entitySavedPosesBySource.get(uuid);
        if (sources != null) for (var poses : sources.values()) held |= held(poses);
        return held;
    }

    private static int held(strm.emfcompat.core.SavedPoses poses) {
        if (poses == null) return 0;
        var parts = poses.parts();
        int held = 0;
        if (poses.rightArm() != null || parts != null && parts.containsKey("right_arm")) held |= RIGHT;
        if (poses.leftArm() != null || parts != null && parts.containsKey("left_arm")) held |= LEFT;
        if (parts != null && (parts.containsKey("body") || parts.containsKey("head"))) held |= TORSO | RIGHT | LEFT;
        return held;
    }

    private static boolean charged(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.CrossbowItem && net.minecraft.world.item.CrossbowItem.isCharged(stack);
    }

    private static int arm(AbstractClientPlayer player, InteractionHand hand) {
        HumanoidArm side = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        return side == HumanoidArm.RIGHT ? RIGHT : LEFT;
    }

    /** What the ride asks of the torso: the jockey's lean; {@code null} off a horse. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.riding || !INSTANCE.isEnabled() || (state.busy & TORSO) != 0) return null;
        // Leaning forward is +xRot: the jockey's lean, the saddle's turn partly taken back, the body on its spring
        // asked a little ahead.
        float pitch = Math.min(MOST_LEAN, jockey(state) + COUNTER * state.noseUp
                + Mth.clamp(state.pitch + state.pitchRate * LEAD, -BACK_LIMIT, FORWARD_LIMIT + BOW_LIMIT));
        float roll = Mth.clamp(state.roll + state.rollRate * LEAD, -SIDE_LIMIT, SIDE_LIMIT);
        return TorsoLean.Hint.turn(pitch * state.weight, 0f, roll * state.weight);
    }

    /** The legs gripping the horse's sides; before anything that works from where they are. */
    public static void legs(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.riding || state.weight < 1e-3f || !INSTANCE.isEnabled()) return;
        ModelPart r = parts.apply("right_leg"), l = parts.apply("left_leg");
        if (r == null || l == null) return;
        float w = state.weight;
        // Sat back to the middle of the saddle, and thrown up off it, the whole rider rises.
        Riders.shift(parts, SIT_BACK * w, state.rise * w);
        // Hanging straight down the barrel, spread to its width; turned out from the hip, the right leg is +zRot.
        r.x += (-LEG_X - r.x) * w;
        l.x += (LEG_X - l.x) * w;
        r.xRot += (0f - r.xRot) * w;
        l.xRot += (0f - l.xRot) * w;
        r.yRot += (0f - r.yRot) * w;
        l.yRot += (0f - l.yRot) * w;
        r.zRot += (LEG_ROLL - r.zRot) * w;
        l.zRot += (-LEG_ROLL - l.zRot) * w;
    }

    /** The last word on the hands and the head: each fist kept on its place on the reins, the head on the look. */
    public static void grip(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !state.riding || !INSTANCE.isEnabled()) return;
        ModelPart head = parts.apply("head"), hat = parts.apply("hat");
        if (head != null && (state.busy & TORSO) == 0 && !strm.emfcompat.core.EMFCompatCore.isLocalPlayerInFirstPerson(uuid)
                && InteractionRuntime.aim(uuid, Effector.HEAD) == null) {
            strm.touchnmotion.seat.SeatedBody.turnHead(head, hat, state.headYaw, state.headPitch, state.weight);
        }
        Riders.grip(uuid, parts, INSTANCE.id(), state.place, state.gap);
        if (strm.touchnmotion.DebugLog.trace() && state.trace.due(30_000_000L)) {
            ModelPart body = parts.apply("body");
            float[] lean = TorsoLean.lean(uuid);
            TorsoLean.Hint hint = torsoHint(uuid);
            org.slf4j.LoggerFactory.getLogger("EMFCompatRide").info("[HorseTrace] noseUp={} shove=({} {} {}) off=({} {} {}) pitch={} roll={} rise={} hint={} lean={} bodyPitch={} gapR={} gapL={}",
                    r2(Math.toDegrees(state.noseUp)), r2(state.shove.x), r2(state.shove.y), r2(state.shove.z),
                    r2(state.off[0] * 16), r2(state.off[1] * 16), r2(state.off[2] * 16),
                    r2(Math.toDegrees(state.pitch)), r2(Math.toDegrees(state.roll)), r2(state.rise),
                    hint == null ? 0 : r2(Math.toDegrees(hint.pitch())), lean == null ? 0 : r2(Math.toDegrees(lean[0])),
                    body == null ? 0 : r2(Math.toDegrees(body.xRot)), r2(state.gap[0]), r2(state.gap[1]));
        }
    }

    private static double r2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
