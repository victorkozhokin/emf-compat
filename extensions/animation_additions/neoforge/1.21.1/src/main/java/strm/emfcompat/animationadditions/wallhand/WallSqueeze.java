package strm.emfcompat.animationadditions.wallhand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.animationadditions.motion.MotionRuntime;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.EMFCompatCore;

import java.util.UUID;
import java.util.function.Function;

/**
 * Past a wall the arms stay out of it. The model is wider than the player's box - the arms' outer
 * sides are 0.47 of a block from the middle, the box 0.3 - so right against a wall an arm is drawn
 * inside it. How much room there is on each side is measured, at the height of the shoulder and of
 * the hand; with less than the arm needs:
 *
 * <ul>
 *   <li>room on the other side - the upper body is shifted over that way, a pixel and a half at most;</li>
 *   <li>what is still in a wall after that - a wall right against one side, or a gap narrower than the
 *   shoulders - the torso turns, a shoulder first, until it fits, the head still looking where it looked.</li>
 * </ul>
 *
 * <p>Turned to fit, the hands go onto the walls and, moving, push along them ({@link #aimArms}).
 * Squeezed, the arms are kept still, and turned to fit they come up from the sides, as far as the walls let them: a turned torso swings its arms across the gap,
 * into the walls. The legs are inside the box as they are. The torso's part is asked of
 * {@code TorsoLean} ({@link #torsoHint}); any player's, moving or not.</p>
 */
public final class WallSqueeze {

    public static final String KEY_ENABLED = "wallhand.squeeze";

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatWallSqueeze");
    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    /** The player model's scale: a model pixel is this many blocks. */
    private static final double PIXEL = 0.9375 / 16;
    /** The arm's outer side from the middle of the body, pixels: the shoulder pivot 6 out, the arm 2 either way of it. */
    private static final float SHOULDER = 6f, ARM_HALF = 2f;
    /** Room an arm needs from the middle, blocks: its outer side and a little air. */
    private static final double NEED = (SHOULDER + ARM_HALF) * PIXEL + 0.01;
    /** How far to a side a wall is looked for, blocks. */
    private static final double LOOK = 0.75;
    /** Where the rays start: shares of the player's height up, and blocks ahead and behind the middle - the arm's thickness. */
    private static final double[] HEIGHTS = {0.72, 0.45};
    private static final double[] ALONG = {-0.14, 0, 0.14};
    /** Going over to the free side: the shift at most, pixels. */
    private static final float MAX_SHIFT = 1.5f;
    /** The turn at most; and in the wall on both sides by this much, pixels, it is all there. */
    private static final float MAX_TURN = (float) Math.toRadians(68), TURN_FULL_AT = 1.5f;
    private static final double SECONDS = 0.12;
    /** Squeezed, the share of their swing the arms keep. */
    private static final float ARM_SWING_KEPT = 0.2f;
    /**
     * Turned to fit, the arms come up from the sides towards a T - along the gap, one ahead and one
     * behind: this far at most, radians, and each only as far as its hand stays out of the wall on
     * its side. The torso turns this much past what the shoulders need, to leave the arms that room.
     */
    private static final float ARM_UP = (float) Math.toRadians(40), TURN_PAST = (float) Math.toRadians(8);
    private static final float ARM_LENGTH = 12f;
    private static final float NO_WALL = 1e6f;
    /**
     * A hand on a wall: from the shoulder to the palm, pixels; the arm no nearer the wall than half
     * its thickness; the palm this far below the shoulder at rest; and no wall further than this
     * from the shoulder is reached for.
     */
    private static final float PALM = 10f, PALM_BELOW = 3f, WALL_REACH = 9f;
    /**
     * Moving, the hands help along, one after the other: the palm goes ahead along the wall and up -
     * the arm out from the body - and comes back down towards it, this much either way of its rest,
     * radians, once every {@link #PUSH_EVERY} blocks walked.
     */
    private static final float PUSH = 0.38f;
    private static final double PUSH_EVERY = 1.3;

    private WallSqueeze() {
    }

    private static final class State {
        /** Asked of the torso this frame: the turn, radians; the shift, pixels (+ is the model's left). */
        float turn, shift;
        /** How squeezed the arms are, 0..1, smoothed. */
        float arms;
        /** How far up from the side each arm is held, radians, smoothed. */
        float upRight, upLeft;
        /** The walls, pixels from the middle of the body to the right and to the left; huge with none. */
        float wallRight = NO_WALL, wallLeft = NO_WALL;
        /** How much the hands are on the walls, 0..1, smoothed; how much the player moves, 0..1; which way, -1..1. */
        float hands, moving, way = 1f;
        /** Where in its cycle the pushing along is, radians. */
        float phase;
        String logged = "off";
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Keep out of walls", true,
                "On", "Right against a wall, or in a gap narrower than the shoulders, the torso turns to fit.",
                "Off", "The arms go through a wall the player stands against, as in vanilla.");
    }

    /** Called right before the model is animated, before the torso. */
    public static void modelPose(AbstractClientPlayer player) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        EntityStates.Entry<State> entry = STATES.seen(uuid, now);
        double dt = EntityStates.due(entry, now);
        if (dt < 0) return;
        State s = entry.value;
        s.turn = s.shift = 0f;
        String decided = "off";
        boolean on = EMFCompatConfig.getBoolean(KEY_ENABLED, true) && EMFCompatCore.isCompatEnabled()
                && !EMFCompatCore.isLocalPlayerInFirstPerson(uuid)
                && !player.isPassenger() && !player.isSleeping() && !player.isFallFlying() && !player.isSwimming()
                && (player.getPose() == Pose.STANDING || player.getPose() == Pose.CROUCHING);
        float squeezed = 0f, upRight = 0f, upLeft = 0f;
        if (on) {
            float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            Vec3 at = player.getPosition(partial);
            double yaw = Math.toRadians(Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
            // Minecraft yaw: 0 faces +z; forward (-sin, cos), the right of it (-cos, -sin).
            Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
            double roomRight = room(player, at, forward, right), roomLeft = room(player, at, forward, right.scale(-1));
            // How far each arm is in its wall, and how far the body can go the other way, pixels.
            float inRight = (float) (Math.max(0, NEED - roomRight) / PIXEL), inLeft = (float) (Math.max(0, NEED - roomLeft) / PIXEL);
            float spareRight = (float) (Math.max(0, roomRight - NEED) / PIXEL), spareLeft = (float) (Math.max(0, roomLeft - NEED) / PIXEL);
            // Over to the model's left (+x) off a wall on the right, as far as there is room on the left; and the other way.
            float over = Math.min(inRight, Math.min(spareLeft, MAX_SHIFT)) - Math.min(inLeft, Math.min(spareRight, MAX_SHIFT));
            s.shift = over;
            // What is left in a wall after that: the torso turns to fit, the shoulder at the nearer wall first.
            float left = Math.max(Math.max(0f, inRight - Math.max(0f, over)), Math.max(0f, inLeft - Math.max(0f, -over)));
            if (left > 0.01f) {
                float full = Mth.clamp(left / TURN_FULL_AT, 0f, 1f);
                float turn = Math.min(MAX_TURN, fitting(left) + TURN_PAST);
                s.turn = full * turn * (inRight >= inLeft ? 1f : -1f);
                // The room each side has from where the shifted body is, pixels.
                s.wallRight = roomRight < LOOK ? (float) (roomRight / PIXEL) : NO_WALL;
                s.wallLeft = roomLeft < LOOK ? (float) (roomLeft / PIXEL) : NO_WALL;
                upRight = full * up(turn, (float) (roomRight / PIXEL) + over);
                upLeft = full * up(turn, (float) (roomLeft / PIXEL) - over);
                squeezed = full;
                decided = "turn";
            } else if (Math.abs(over) > 0.05f) {
                squeezed = Math.min(1f, Math.abs(over) / MAX_SHIFT);
                decided = over > 0 ? "shift-L" : "shift-R";
            }
        }
        s.arms += (squeezed - s.arms) * Smoothing.follow(dt, SECONDS);
        s.hands += ((decided.equals("turn") ? squeezed : 0f) - s.hands) * Smoothing.follow(dt, SECONDS);
        MotionRuntime.Motion motion = MotionRuntime.get(uuid);
        s.moving += (Mth.clamp(motion.speed(), 0f, 1f) - s.moving) * Smoothing.follow(dt, 0.2);
        if (Math.abs(motion.forward()) > 0.3f) s.way += (Math.signum(motion.forward()) - s.way) * Smoothing.follow(dt, 0.15);
        s.phase = (float) ((s.phase + 2 * Math.PI * motion.speed() / PUSH_EVERY * dt) % (2 * Math.PI));
        s.upRight += (upRight - s.upRight) * Smoothing.follow(dt, SECONDS);
        s.upLeft += (upLeft - s.upLeft) * Smoothing.follow(dt, SECONDS);
        if (!decided.equals(s.logged)) {
            s.logged = decided;
            LOGGER.info("[WallSqueeze] {} {}", player.getName().getString(), decided);
        }
    }

    /** The least room from the middle of the body to a wall on one side, blocks; {@link #LOOK} with none. */
    private static double room(AbstractClientPlayer player, Vec3 at, Vec3 forward, Vec3 side) {
        double room = LOOK;
        for (double height : HEIGHTS) {
            for (double along : ALONG) {
                Vec3 from = at.add(forward.scale(along)).add(0, player.getBbHeight() * height, 0);
                BlockHitResult hit = player.level().clip(new ClipContext(from, from.add(side.scale(LOOK)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (hit.getType() == HitResult.Type.MISS || hit.isInside()) continue;
                room = Math.min(room, hit.getLocation().subtract(from).dot(side));
            }
        }
        return room;
    }

    /**
     * The turn at which the shoulders are narrower by {@code pixels} a side: turned by a, an arm's
     * outer side is {@code 6 cos a + 2 (cos a + sin a)} out - wider at first, its corner coming
     * round, and narrower only past 28 degrees.
     */
    private static float fitting(float pixels) {
        float wanted = SHOULDER + ARM_HALF - pixels;
        for (float turn = 0f; turn < MAX_TURN; turn += 0.01f) {
            float out = SHOULDER * Mth.cos(turn) + ARM_HALF * (Mth.cos(turn) + Mth.sin(turn));
            if (turn > 0.3f && out <= wanted) return turn;
        }
        return MAX_TURN;
    }

    /**
     * How far up an arm can come with the torso turned by {@code turn} and {@code room} pixels from
     * the middle to the wall on its side: its hand is {@code 12 sin up} further out along the
     * shoulders, which is {@code cos turn} of that towards the wall.
     */
    private static float up(float turn, float room) {
        float cos = Mth.cos(turn), sin = Mth.sin(turn);
        float hand = (room - 0.3f - ARM_HALF * (cos + sin)) / Math.max(0.05f, cos) - SHOULDER;
        return (float) Math.asin(Mth.clamp(hand / ARM_LENGTH, 0f, Mth.sin(ARM_UP)));
    }

    /** For {@code TorsoLean}: {pitch, yaw, roll, shift (pixels), the part of the yaw the head does not take}; {@code null} for none. */
    public static float[] torsoHint(UUID uuid) {
        State s = STATES.fresh(uuid);
        if (s == null || s.turn == 0f && s.shift == 0f) return null;
        return new float[]{0f, s.turn, 0f, s.shift, s.turn};
    }

    /** The arms close and still. Called after the pack has animated, before the torso and the hands' aims. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        State s = STATES.fresh(uuid);
        if (s == null || s.arms < 1e-3f) return;
        for (int i = 0; i < 2; i++) {
            ModelPart arm = parts.apply(i == 0 ? "right_arm" : "left_arm");
            if (arm == null) continue;
            // Up from the side, for a hanging right arm, is +zRot; for a left one -zRot.
            float out = i == 0 ? s.upRight : -s.upLeft;
            arm.xRot = Mth.lerp(s.arms, arm.xRot, arm.xRot * ARM_SWING_KEPT);
            arm.zRot = Mth.lerp(s.arms, arm.zRot, out);
        }
    }

    /**
     * The hands on the walls. Turned to fit, each arm goes to the wall nearer its shoulder - which
     * wall that is goes by where the turn has put the shoulder - the palm a little below the
     * shoulder and ahead along the wall, as far as the arm's length leaves; moving, the palms push
     * along in turn. An arm with no wall in reach keeps its place up from the side. Called after the
     * torso has turned: the arm is aimed from where its shoulder is drawn.
     */
    public static void aimArms(UUID uuid, Function<String, ModelPart> parts) {
        State s = STATES.fresh(uuid);
        if (s == null || s.hands < 1e-3f) return;
        for (int i = 0; i < 2; i++) {
            boolean right = i == 0;
            if (InteractionRuntime.weight(uuid, right ? Effector.RIGHT_ARM : Effector.LEFT_ARM) > 0.01f) continue;
            ModelPart arm = parts.apply(right ? "right_arm" : "left_arm");
            if (arm == null) continue;
            // Model space: pixels, y down, forwards -z, the right wall at -x.
            float toRight = arm.x + s.wallRight, toLeft = s.wallLeft - arm.x;
            boolean onRight = toRight <= toLeft;
            float across = Math.min(toRight, toLeft) - ARM_HALF;
            if (across > WALL_REACH) continue;
            across = Math.max(0f, across);
            // What the arm's length leaves in the wall's plane, shared between ahead and down.
            float inPlane = (float) Math.sqrt(PALM * PALM - across * across);
            float rest = (float) Math.asin(Math.min(1f, PALM_BELOW / inPlane));
            float down = Mth.clamp(rest + PUSH * s.moving * Mth.sin(s.phase + (right ? 0f : Mth.PI)), -0.3f, 1.3f);
            float dx = (onRight ? -across : across) / PALM;
            float dy = inPlane * Mth.sin(down) / PALM;
            float dz = -inPlane * Mth.cos(down) * s.way / PALM;
            // The arm hangs along +y; turned by x then z it points along (-sin z cos x, cos z cos x, sin x).
            float x = (float) Math.asin(Mth.clamp(dz, -1f, 1f));
            float z = (float) Math.atan2(-dx, dy);
            arm.xRot = Mth.lerp(s.hands, arm.xRot, x);
            arm.yRot = Mth.lerp(s.hands, arm.yRot, 0f);
            arm.zRot = Mth.lerp(s.hands, arm.zRot, z);
        }
    }
}
