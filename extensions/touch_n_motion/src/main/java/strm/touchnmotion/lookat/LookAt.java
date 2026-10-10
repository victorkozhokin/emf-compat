package strm.touchnmotion.lookat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.touchnmotion.DebugLog;
import strm.touchnmotion.interaction.Body;
import strm.touchnmotion.interaction.Candidate;
import strm.touchnmotion.interaction.Effector;
import strm.touchnmotion.interaction.EntityStates;
import strm.touchnmotion.interaction.FrameClock;
import strm.touchnmotion.interaction.InteractionContext;
import strm.touchnmotion.interaction.InteractionProvider;
import strm.touchnmotion.interaction.InteractionRuntime;
import strm.touchnmotion.motion.Spring;
import strm.touchnmotion.torso.PelvisFollow;
import strm.touchnmotion.torso.Stride;
import strm.touchnmotion.torso.TorsoLean;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.ik.IKFrame;
import strm.emfcompat.core.ik.IKMath;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Looking at things. Two ways to come by something to look at:
 *
 * <ul>
 * <li>left standing idle for a while - no walking, the camera left alone, nothing done - the head
 * turns to the nearest creature of a kind the settings name (players, villagers, animals,
 * monsters);</li>
 * <li>the look resting on a sign close by, at once: the head settles on its board and the body
 * leans in to read ({@link SignRead}).</li>
 * </ul>
 *
 * <p>The head is not taken from the pack: its turn to the thing is laid over the pack's own head
 * by a weight on a spring, so it goes there and comes back eased, from wherever it is, and follows
 * the camera all the way back. Anything else that wants the head (the runtime's head slot) is
 * drawn after this and so over it.</p>
 *
 * <p>A creature too far round for the neck alone is turned to with the body: only the drawn model
 * is turned ({@link #orient}), never the game's own facing, and the feet are kept where they stand
 * on the ground while it turns and brought under it one short step at a time - and back the same
 * way when the look is over.</p>
 */
public final class LookAt implements InteractionProvider {

    public static final LookAt INSTANCE = new LookAt();
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatLookAt");

    public static final String KEY_ENABLED = "lookat.enabled";
    public static final String KEY_IDLE = "lookat.idleSeconds";
    public static final String KEY_PLAYERS = "lookat.players", KEY_VILLAGERS = "lookat.villagers",
            KEY_ANIMALS = "lookat.animals", KEY_MONSTERS = "lookat.monsters";
    public static final String KEY_STEPS = "lookat.steps";

    /** Idle this long, seconds, before the head starts looking round, when the setting says nothing. */
    private static final double IDLE_SECONDS = 5.0;
    private static final double[] IDLE_CHOICES = {1, 2, 3, 5, 8, 12, 20, 30};
    /** A turn of the camera smaller than this, degrees, still counts as idle. */
    private static final float LOOK_JITTER = 0.5f;
    private static final double RANGE = 8.0;
    /** Who to look at is chosen this often, not every frame: the creatures round the player and a line of sight to each. */
    private static final long PICK_EVERY_NANOS = 150_000_000L;
    /** How far the head turns from the body at most. */
    static final float NECK_YAW = (float) Math.toRadians(70);
    static final float NECK_PITCH = (float) Math.toRadians(60);
    /** The neck's easy turn: past it the body comes round too, by this much at most, in steps of the feet. */
    private static final float EASY_YAW = (float) Math.toRadians(35), TURN_LIMIT = (float) Math.toRadians(70);
    /** The body is not sent after every small move of the creature: only a change this big, radians. */
    private static final float TURN_SLACK = (float) Math.toRadians(10);
    /** A foot is stepped under the turned body once it is this far round from it, radians; a step lasts this long. */
    private static final float STEP_AT = (float) Math.toRadians(14);
    /** The body waits for the feet: it is never further round than this from a sole on the ground, radians. */
    private static final float FEET_BEHIND = (float) Math.toRadians(28);
    /** Stood still and turned, a sole less than this off the body is left as it is, radians - but brought right under it when the turn is over. */
    private static final float STEP_LEAST = (float) Math.toRadians(5), STEP_HOME = (float) Math.toRadians(0.5);
    private static final float STEP_SECONDS = 0.26f, STEP_HEIGHT = 0.5f;
    /** Half-lives, seconds: the look coming on and going; the aim following the thing; the body's turn, and its giving up when walked off. */
    private static final double ON = 0.14, OFF = 0.15, FOLLOW = 0.09, TURN = 0.2, TURN_DROP = 0.05;
    /** Radians: the lean in towards a sign at arm's length. Blocks: a sign's reading distance. */
    private static final float SIGN_LEAN = 0.1f;

    private LookAt() {
    }

    private static final class State {
        float lastYaw = Float.NaN, lastPitch;
        int lastSlot = -1;
        boolean lastCrouching;
        long idleSince, pickedAt;
        int targetId = -1;
        final SignRead.Rest sign = new SignRead.Rest();
        /** How much of the look shows, the aim (model space), and how near a read sign is. */
        final Spring weight = new Spring(), yaw = new Spring(), pitch = new Spring();
        float signNear, reading;
        /** The drawn model's turn to the thing, radians (positive to the right), and where it is going. */
        final Spring turn = new Spring();
        float turnTarget;
        /** The feet under a turning body: each sole's turn when it was last put down. */
        final Stride stride = new Stride();
        final float[] planted = new float[2];
        final FrameClock clock = new FrameClock();
        final DebugLog.Pace tracePace = new DebugLog.Pace();
        AbstractClientPlayer player;
    }

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Look at things nearby", true,
                "On", "Left standing idle for a while, the head turns to the nearest creature in view; do anything and it eases back to where you look.",
                "Off", "The head always follows the camera.");
        String[] texts = new String[IDLE_CHOICES.length];
        for (int i = 0; i < texts.length; i++) texts[i] = (int) IDLE_CHOICES[i] + " s";
        config.addChoice(KEY_ENABLED, KEY_IDLE, "Idle time before looking", IDLE_CHOICES, texts, IDLE_SECONDS,
                "How long you must stand still, the camera left alone, before the head looks round at a creature.");
        config.addChild(KEY_ENABLED, KEY_PLAYERS, "Players", true, "On", "Other players are looked at.", "Off", "Other players are not looked at.");
        config.addChild(KEY_ENABLED, KEY_VILLAGERS, "Villagers and traders", true, "On", "Villagers and wandering traders are looked at.", "Off", "Villagers and traders are not looked at.");
        config.addChild(KEY_ENABLED, KEY_ANIMALS, "Animals", true, "On", "Animals and other peaceful creatures are looked at.", "Off", "Animals are not looked at.");
        config.addChild(KEY_ENABLED, KEY_MONSTERS, "Monsters", false, "On", "Hostile creatures are looked at.", "Off", "Hostile creatures are not looked at.");
        config.addChild(KEY_ENABLED, SignRead.KEY_ENABLED, "Read signs", true,
                "On", "Looking at a sign close by, the head settles on it at once and the body leans in.",
                "Off", "A sign is looked at like anything else.");
        config.addChild(KEY_ENABLED, KEY_STEPS, "Turn the body in steps", true,
                "On", "Standing, a creature too far round for the neck is turned to with the whole body, the feet stepping round - and back.",
                "Off", "Only the head turns, as far as a neck goes.");
    }

    @Override
    public String id() {
        return "LookAt";
    }

    @Override
    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        AbstractClientPlayer player = context.player();
        IKFrame frame = context.frame();
        long now = context.now();
        double dt = context.dt();
        State state = STATES.seen(player.getUUID(), now).value;
        state.player = player;

        // Idle: standing and nothing done for a while. Walking, the camera turning, a swing, an
        // item in use, a jump, a crouch or another hotbar slot all count as doing something.
        float yRot = player.getYRot(), xRot = player.getXRot();
        boolean looked = Float.isNaN(state.lastYaw)
                || Math.abs(yRot - state.lastYaw) > LOOK_JITTER || Math.abs(xRot - state.lastPitch) > LOOK_JITTER;
        int slot = strm.touchnmotion.platform.Platform.selectedSlot(player);
        boolean crouching = player.isCrouching();
        boolean still = player.isPassenger() ? player.getVehicle().getDeltaMovement().horizontalDistanceSqr() < .0004 : Body.planted(player);
        boolean acted = looked || !still || strm.touchnmotion.platform.Platform.swinging(player) || player.isUsingItem()
                || slot != state.lastSlot || crouching != state.lastCrouching;
        state.lastYaw = yRot;
        state.lastPitch = xRot;
        state.lastSlot = slot;
        state.lastCrouching = crouching;
        if (acted || state.idleSince == 0) state.idleSince = now;
        boolean idle = (now - state.idleSince) / 1e9 >= EMFCompatConfig.getNumber(KEY_IDLE, IDLE_SECONDS);

        // A sign under the look is read at once; a creature is looked at only out of idleness.
        Vec3 point = null;
        String name = null;
        BlockPos sign = player.isSleeping() ? null : SignRead.resting(player, state.sign, now);
        if (sign != null) {
            Vec3 board = SignRead.board(player.level(), sign);
            float[] aim = aim(frame, board);
            // Behind the shoulder a sign is not read: the look is on it only by the camera's freedom.
            if (Math.abs(aim[0] + state.turn.value) <= NECK_YAW && Math.abs(aim[1]) <= NECK_PITCH) {
                point = board;
                name = "sign " + sign.toShortString();
                state.signNear = (float) Mth.clamp(1.0 - (board.distanceTo(player.getEyePosition()) - 1.0) / (SignRead.RANGE - 1.0), 0.0, 1.0);
            }
        }
        boolean reading = point != null;
        boolean steps = steps(player);
        LivingEntity target = null;
        if (reading || !idle || player.isSleeping()) {
            state.pickedAt = 0;
        } else if (now - state.pickedAt >= PICK_EVERY_NANOS) {
            target = pick(player, frame, state, steps ? NECK_YAW + TURN_LIMIT - TURN_SLACK : NECK_YAW);
            state.pickedAt = now;
        } else if (state.targetId != -1 && player.level().getEntity(state.targetId) instanceof LivingEntity kept && kept.isAlive()) {
            // Between two looks round the head stays on the one it has.
            target = kept;
        }
        state.targetId = target == null ? -1 : target.getId();
        if (target != null) {
            point = target.getEyePosition(strm.touchnmotion.platform.Platform.partialTick(true));
            name = target.getName().getString();
        }

        // The body's share: what the neck does not take easily, and only for a creature.
        float turnTo = 0f;
        float[] aim = point == null ? null : aim(frame, point);
        if (target != null && steps) {
            float round = aim[0] + state.turn.value;
            float want = Mth.clamp(round - Mth.clamp(round, -EASY_YAW, EASY_YAW), -TURN_LIMIT, TURN_LIMIT);
            turnTo = Math.abs(want - state.turnTarget) > TURN_SLACK ? want : state.turnTarget;
        }
        state.turnTarget = turnTo;
        if (still) {
            // No further round than the feet have come: the turn waits for the steps, there and back.
            float low = Math.max(state.planted[0], state.planted[1]) - FEET_BEHIND, high = Math.min(state.planted[0], state.planted[1]) + FEET_BEHIND;
            state.turn.update(Mth.clamp(turnTo, low, high), TURN, dt);
            if (state.turn.value < low || state.turn.value > high) state.turn.set(Mth.clamp(state.turn.value, low, high));
        } else {
            state.turn.update(turnTo, TURN_DROP, dt);
        }

        if (aim != null) {
            float yaw = Mth.clamp(aim[0], -NECK_YAW, NECK_YAW), pitch = Mth.clamp(aim[1], -NECK_PITCH, NECK_PITCH);
            if (state.weight.value < 0.02f) {
                // Nothing of it shows yet: start on the thing, not on whatever was looked at last.
                state.yaw.set(yaw);
                state.pitch.set(pitch);
            } else {
                state.yaw.update(yaw, FOLLOW, dt);
                state.pitch.update(pitch, FOLLOW, dt);
            }
        }
        // With the body turned the head has the further to come back: it takes its time.
        state.weight.update(aim != null ? 1f : 0f, aim != null ? ON : OFF * (1 + Math.abs(state.turn.value) / TURN_LIMIT), dt);
        state.reading += ((reading ? 1f : 0f) - state.reading) * strm.touchnmotion.interaction.Smoothing.follow(dt, 0.2);
        context.decide(name != null ? "-> " + name + (Math.abs(state.turnTarget) > 1e-3f ? " turned" : "")
                : state.weight.value > 0.02f || Math.abs(state.turn.value) > 0.02f ? "back" : "camera");
    }

    private static boolean steps(AbstractClientPlayer player) {
        return EMFCompatConfig.getBoolean(KEY_STEPS, true) && Body.planted(player) && !player.isCrouching()
                && !strm.touchnmotion.compat.WholeBody.seated(player)
                && !strm.touchnmotion.wallhand.FenceLean.squared(player.getUUID());
    }

    /** Whether a creature is of a kind the settings have looked at. */
    private static boolean wanted(LivingEntity e) {
        if (e instanceof Player) return EMFCompatConfig.getBoolean(KEY_PLAYERS, true);
        if (e instanceof AbstractVillager) return EMFCompatConfig.getBoolean(KEY_VILLAGERS, true);
        if (e instanceof Enemy) return EMFCompatConfig.getBoolean(KEY_MONSTERS, false);
        return e instanceof Mob && EMFCompatConfig.getBoolean(KEY_ANIMALS, true);
    }

    /** The nearest wanted creature within {@code round} of straight ahead, keeping the current one while it still is. */
    private static LivingEntity pick(AbstractClientPlayer player, IKFrame frame, State state, float round) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RANGE))) {
            if (e == player || !e.isAlive() || e.isInvisible() || e.isSpectator() || !wanted(e)) continue;
            // What the view is from, when that is not the player's own eyes, is no one to look at: a free
            // camera is a player of its own in the world, standing wherever the view has been taken to.
            if (e == minecraft.getCameraEntity() && e != minecraft.player) continue;
            if (player.isPassengerOfSameVehicle(e) || e.hasPassenger(player) || player.hasPassenger(e)) continue;
            double distance = e.distanceTo(player);
            if (distance > RANGE) continue;
            float[] aim = aim(frame, e.getEyePosition());
            // The frame is the turned model's: the turn is added back to judge by the body as the game has it.
            if (Math.abs(aim[0] + state.turn.value) > round || Math.abs(aim[1]) > NECK_PITCH) continue;
            if (!player.hasLineOfSight(e)) continue;
            if (e.getId() == state.targetId) return e;
            if (distance < bestDistance) {
                best = e;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** {yaw, pitch} of the head, model space, that points it at {@code point} in the world. */
    static float[] aim(IKFrame frame, Vec3 point) {
        Vector3f head = frame.relativeToJoint(point, new Vector3f(0, 0, 0));
        // Model pixels from the neck pivot; model forward is -z, down is +y.
        head.normalize();
        float yaw = (float) Math.atan2(-head.x, -head.z);
        float pitch = (float) Math.asin(Math.max(-1f, Math.min(1f, head.y)));
        return new float[]{yaw, pitch};
    }

    /** Whether the drawn model is turned off the game's facing by a look. */
    public static boolean turned(UUID uuid) {
        State state = STATES.fresh(uuid);
        return state != null && Math.abs(state.turn.value) > 1e-3f && INSTANCE.isEnabled();
    }

    /** What reading a sign asks of the torso: in towards it, the nearer it is; {@code null} when none is read. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || state.reading < 1e-3f || !INSTANCE.isEnabled()) return null;
        return TorsoLean.Hint.turn(SIGN_LEAN * state.signNear * state.reading, 0f, 0f);
    }

    /** Turns only the drawn model towards the creature looked at; the game's own facing is left alone. */
    public static void orient(AbstractClientPlayer player, PoseStack stack) {
        State state = STATES.fresh(player.getUUID());
        if (state == null || Math.abs(state.turn.value) < 1e-3f || !INSTANCE.isEnabled() || player.isPassenger()
                || !EMFCompatCore.isCompatEnabled() || EMFCompatCore.isLocalPlayerInFirstPerson(player.getUUID())) return;
        strm.touchnmotion.platform.Platform.rotate(stack, new Quaternionf().rotationY(state.turn.value));
    }

    /**
     * The feet under a turning body, before the torso: each sole stays where it stands on the
     * ground while the model turns over it, and once that is far enough round it is lifted and put
     * down under the body - one at a time, on the way there and on the way back alike.
     */
    public static void support(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || state.player == null || !INSTANCE.isEnabled()) return;
        float turn = state.turn.value;
        Stride stride = state.stride;
        if (Math.abs(turn) < 1e-3f && stride.stepping < 0 && Math.abs(state.planted[0]) < 1e-3f && Math.abs(state.planted[1]) < 1e-3f) {
            state.planted[0] = state.planted[1] = 0f;
            stride.clear();
            return;
        }
        ModelPart[] legs = {parts.apply("right_leg"), parts.apply("left_leg")};
        if (legs[0] == null || legs[1] == null) return;
        double dt = state.clock.tick();
        if (dt >= 0) {
            if (!Body.planted(state.player)) {
                // Walked off: the pack's stride has the feet, and the turn is dropped under them.
                stride.settle(dt, .1);
                state.planted[0] = state.planted[1] = turn;
            } else {
                boolean home = Math.abs(state.turnTarget) < 1e-3f;
                boolean settled = Math.abs(state.turn.velocity) < 0.05f;
                if (stride.stepping < 0) {
                    int foot = Math.abs(turn - state.planted[0]) >= Math.abs(turn - state.planted[1]) ? 0 : 1;
                    float off = Math.abs(turn - state.planted[foot]);
                    if (off > STEP_AT || settled && off > (home ? STEP_HOME : STEP_LEAST)) stride.begin(foot, new Vector3f());
                    else if (home && settled && Math.abs(turn) < STEP_HOME) state.planted[0] = state.planted[1] = turn;
                }
                for (int i = 0; i < 2; i++) if (i != stride.stepping) stride.feet[i].set(stood(legs[i], turn - state.planted[i]));
                if (stride.stepping >= 0) {
                    int foot = stride.stepping;
                    stride.advance(dt, STEP_SECONDS);
                    // Put down under the body as it is turned now.
                    if (stride.stepping < 0) state.planted[foot] = turn;
                }
            }
        }
        Vector3f[] soles = stride.drawn(STEP_HEIGHT);
        float[] twist = new float[2];
        for (int i = 0; i < 2; i++) {
            float off = turn - state.planted[i];
            // The foot in the air comes round to the body as it goes.
            twist[i] = -(i == stride.stepping ? off * (1f - strm.touchnmotion.interaction.Ease.smooth(stride.progress)) : off);
        }
        PelvisFollow.step(parts, soles[0], soles[1], twist[0], twist[1]);
        if (DebugLog.trace() && state.tracePace.due(100_000_000L))
            LOGGER.info("[LookTurn] turn={} to={} planted={},{} step={} progress={}", Math.toDegrees(turn), Math.toDegrees(state.turnTarget),
                    Math.toDegrees(state.planted[0]), Math.toDegrees(state.planted[1]), stride.stepping, stride.progress);
    }

    /** Where a sole put down {@code off} radians of the body's turn ago stands now, model pixels from under its hip. */
    private static Vector3f stood(ModelPart leg, float off) {
        // The model went round to the right by off; what stands on the ground went round to the left in it.
        float c = Mth.cos(off), s = Mth.sin(off);
        return new Vector3f(leg.x * c - leg.z * s - leg.x, 0f, leg.x * s + leg.z * c - leg.z);
    }

    /** The head, over the pack's and under whatever else takes it: towards the thing by the look's weight. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || !INSTANCE.isEnabled()) return;
        float weight = Mth.clamp(state.weight.value, 0f, 1f), turn = state.turn.value;
        if (weight < 1e-3f && Math.abs(turn) < 1e-3f) return;
        ModelPart head = parts.apply("head"), hat = parts.apply("hat");
        if (head == null || InteractionRuntime.aim(uuid, Effector.HEAD) != null) return;
        // The pack's head is where the camera looks from the body as the game has it: taken round with the turned model, it is turned back.
        float base = Mth.clamp(head.yRot - turn, -NECK_YAW - 0.26f, NECK_YAW + 0.26f);
        head.yRot = base + IKMath.wrap(state.yaw.value - base) * weight;
        head.xRot += IKMath.wrap(state.pitch.value - head.xRot) * weight;
        if (hat != null) {
            hat.yRot = head.yRot;
            hat.xRot = head.xRot;
        }
    }
}
