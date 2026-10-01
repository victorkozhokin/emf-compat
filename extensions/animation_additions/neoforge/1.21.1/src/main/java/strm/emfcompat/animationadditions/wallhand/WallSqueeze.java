package strm.emfcompat.animationadditions.wallhand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.animationadditions.interaction.SubLevels;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.ik.IKFrame;

import java.util.List;
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
 * <p>Turned to fit, the hands go onto the walls, one ahead and one behind ({@link #aimArms}).
 * Squeezed, the arms are kept still, and turned to fit they come up from the sides, as far as the walls let them: a turned torso swings its arms across the gap,
 * into the walls. The legs are inside the box as they are. The torso's part is asked of
 * {@code TorsoLean} ({@link #torsoHint}); any player's, moving or not.</p>
 */
public final class WallSqueeze {

    public static final String KEY_ENABLED = "wallhand.squeeze";
    public static final String KEY_TRACE = "wallhand.trace";

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
    private static final float ARM_UP = (float) Math.toRadians(40), TURN_PAST = (float) Math.toRadians(4);
    private static final float ARM_LENGTH = 12f;
    private static final float NO_WALL = 1e6f;
    /**
     * A hand on a wall: from the shoulder to the palm, pixels; the arm no nearer the wall than half
     * its thickness; the palm this far below the shoulder at rest; and no wall further than this
     * from the shoulder is reached for.
     */
    private static final float PALM = 10f, PALM_BELOW = 3f, WALL_REACH = 9f;
    private WallSqueeze() {
    }

    private static final class State {
        /** Asked of the torso this frame: the turn, radians; the shift, pixels (+ is the model's left). */
        float turn, shift;
        /** How squeezed the arms are, 0..1, smoothed. */
        float arms;
        /** Clearance only affects the arm whose side actually has a nearby wall. */
        float rightClearance, leftClearance;
        /** How far up from the side each arm is held, radians, smoothed. */
        float upRight, upLeft;
        /** The walls, pixels from the middle of the body to the right and to the left; huge with none. */
        float wallRight = NO_WALL, wallLeft = NO_WALL;
        /** How much the hands are on the walls, 0..1, smoothed; and which way the torso is turned, 1 to the right, -1 to the left. */
        float hands, side = 1f;
        AbstractClientPlayer player;
        IKFrame frame;
        List<SubLevels.Space> spaces = List.of(SubLevels.WORLD);
        double dt;
        long solvedAt, aimedAt;
        final WallPoseMath.Contact[] contacts = {new WallPoseMath.Contact(), new WallPoseMath.Contact()};
        final Touch[] touches = new Touch[2];
        Vec3 lastPosition;
        String logged = "off";
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Keep out of walls", true,
                "On", "Right against a wall, or in a gap narrower than the shoulders, the torso turns to fit.",
                "Off", "The arms go through a wall the player stands against, as in vanilla.");
        config.addBoolean(KEY_TRACE, "Trace wall contacts", false,
                "On", "Log contact weight, reach and palm clearance for passage tests.",
                "Off", "No per-frame wall contact diagnostics.");
    }

    /** Called right before the model is animated, before the torso. */
    public static void modelPose(AbstractClientPlayer player, IKFrame frame) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        EntityStates.Entry<State> entry = STATES.seen(uuid, now);
        double dt = EntityStates.due(entry, now);
        if (dt < 0) return;
        State s = entry.value;
        if (s.lastPosition != null && s.lastPosition.distanceToSqr(player.position()) > 4) {
            s.arms = s.hands = s.upRight = s.upLeft = 0;
            s.rightClearance = s.leftClearance = 0;
            for (var contact : s.contacts) { contact.weight = 0; contact.known = false; }
        }
        s.lastPosition = player.position();
        s.player = player; s.frame = frame; s.dt = dt; s.solvedAt = now;
        s.spaces = SubLevels.around(player.level(), player.getBoundingBox().inflate(1));
        s.turn = s.shift = 0f;
        s.wallRight = s.wallLeft = NO_WALL;
        String decided = "off";
        boolean on = EMFCompatConfig.getBoolean(KEY_ENABLED, true) && EMFCompatCore.isCompatEnabled()
                && !EMFCompatCore.isLocalPlayerInFirstPerson(uuid)
                && !player.isPassenger() && !player.isSleeping() && !player.isFallFlying() && !player.isSwimming()
                && (player.getPose() == Pose.STANDING || player.getPose() == Pose.CROUCHING);
        float squeezed = 0f, upRight = 0f, upLeft = 0f;
        boolean rightWall = false, leftWall = false;
        if (on) {
            float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            Vec3 at = player.getPosition(partial);
            double yaw = Math.toRadians(Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot));
            // Minecraft yaw: 0 faces +z; forward (-sin, cos), the right of it (-cos, -sin).
            Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
            double roomRight = room(player, at, forward, right, s.spaces), roomLeft = room(player, at, forward, right.scale(-1), s.spaces);
            rightWall = roomRight < NEED;
            leftWall = roomLeft < NEED;
            s.wallRight = roomRight < LOOK ? (float) (roomRight / PIXEL) : NO_WALL;
            s.wallLeft = roomLeft < LOOK ? (float) (roomLeft / PIXEL) : NO_WALL;
            // How far each arm is in its wall, and how far the body can go the other way, pixels.
            float inRight = (float) (Math.max(0, NEED - roomRight) / PIXEL), inLeft = (float) (Math.max(0, NEED - roomLeft) / PIXEL);
            float spareRight = (float) (Math.max(0, roomRight - NEED) / PIXEL), spareLeft = (float) (Math.max(0, roomLeft - NEED) / PIXEL);
            // Over to the model's left (+x) off a wall on the right, as far as there is room on the left; and the other way.
            float over = Math.min(inRight, Math.min(spareLeft, MAX_SHIFT)) - Math.min(inLeft, Math.min(spareRight, MAX_SHIFT));
            s.shift = over;
            // What is left in a wall after that: the torso turns to fit, the shoulder at the nearer wall first.
            float left = Math.max(Math.max(0f, inRight - Math.max(0f, over)), Math.max(0f, inLeft - Math.max(0f, -over)));
            if (left > 0.01f) {
                float full = WallPoseMath.ease(left / TURN_FULL_AT);
                float turn = Math.min(MAX_TURN, fitting(left) + TURN_PAST);
                s.side = WallPoseMath.side(s.side, inRight, inLeft, s.arms > 0.05f);
                s.turn = full * turn * s.side;
                // The room each side has from where the shifted body is, pixels.
                upRight = rightWall ? full * up(turn, (float) (roomRight / PIXEL) + over) : 0;
                upLeft = leftWall ? full * up(turn, (float) (roomLeft / PIXEL) - over) : 0;
                squeezed = full;
                decided = "turn";
            } else if (Math.abs(over) > 0.05f) {
                squeezed = Math.min(1f, Math.abs(over) / MAX_SHIFT);
                decided = over > 0 ? "shift-L" : "shift-R";
            }
        }
        s.arms += (squeezed - s.arms) * Smoothing.follow(dt, SECONDS);
        s.rightClearance += ((rightWall ? squeezed : 0) - s.rightClearance) * Smoothing.follow(dt, SECONDS);
        s.leftClearance += ((leftWall ? squeezed : 0) - s.leftClearance) * Smoothing.follow(dt, SECONDS);
        float grip = decided.equals("turn") && player.onGround() && !player.isSprinting() ? squeezed : 0;
        s.hands += (grip - s.hands) * Smoothing.follow(dt, SECONDS);
        s.upRight += (upRight - s.upRight) * Smoothing.follow(dt, SECONDS);
        s.upLeft += (upLeft - s.upLeft) * Smoothing.follow(dt, SECONDS);
        if (!decided.equals(s.logged)) {
            s.logged = decided;
            LOGGER.info("[WallSqueeze] {} {}", player.getName().getString(), decided);
        }
    }

    /** The least room from the middle of the body to a wall on one side, blocks; {@link #LOOK} with none. */
    private static double room(AbstractClientPlayer player, Vec3 at, Vec3 forward, Vec3 side, List<SubLevels.Space> spaces) {
        double room = LOOK;
        for (double height : HEIGHTS) {
            for (double along : ALONG) {
                Vec3 from = at.add(forward.scale(along)).add(0, player.getBbHeight() * height, 0);
                var hit = WallSurface.clip(player, from, from.add(side.scale(LOOK)), spaces);
                if (hit == null || hit.normal().dot(side) > -0.7) continue;
                room = Math.min(room, hit.position().subtract(from).dot(side));
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

    public static boolean isActive(UUID uuid) {
        State s = STATES.fresh(uuid);
        return s != null && s.hands > 0.05f;
    }

    private static boolean occupied(State s, boolean right) {
        var posed = PoseManager.getSavedPoses(s.player.getUUID());
        if (posed != null && ((right ? posed.rightArm() : posed.leftArm()) != null
                || posed.parts() != null && posed.parts().containsKey(right ? "right_arm" : "left_arm"))) return true;
        boolean main = right == (s.player.getMainArm() == HumanoidArm.RIGHT);
        InteractionHand hand = main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        return !s.player.getItemInHand(hand).isEmpty()
                || s.player.swinging && s.player.swingingArm == hand
                || s.player.isUsingItem() && s.player.getUsedItemHand() == hand;
    }

    private static float ownership(UUID uuid, boolean right) {
        Effector effector = right ? Effector.RIGHT_ARM : Effector.LEFT_ARM;
        // The passive WallHand provider hands over to the squeeze contacts, not an item/swing.
        return Math.max(0, InteractionRuntime.weight(uuid, effector)
                - InteractionRuntime.weight(uuid, effector, "WallHand"));
    }

    /** The arms close and still. Called after the pack has animated, before the torso and the hands' aims. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        State s = STATES.fresh(uuid);
        if (s == null || s.arms < 1e-3f) return;
        for (int i = 0; i < 2; i++) {
            ModelPart arm = parts.apply(i == 0 ? "right_arm" : "left_arm");
            if (arm == null || occupied(s, i == 0)) continue;
            float weight = (i == 0 ? s.rightClearance : s.leftClearance) * (1 - ownership(uuid, i == 0));
            // Up from the side, for a hanging right arm, is +zRot; for a left one -zRot.
            float out = i == 0 ? s.upRight : -s.upLeft;
            arm.xRot = Mth.lerp(weight, arm.xRot, arm.xRot * ARM_SWING_KEPT);
            arm.zRot = Mth.lerp(weight, arm.zRot, out);
        }
    }

    /**
     * The hands on the walls. Turned to fit - to the right against a wall on the right - the right
     * shoulder has gone back and the left one ahead: the right arm is drawn back and reaches for
     * the wall behind it, the left one is put out ahead onto its wall; turned to the left, the other
     * way round. Each to the wall nearer its shoulder, the palm a little below the shoulder and as
     * far along the wall as the arm's length leaves. An arm with no wall on its side retains its
     * resource-pack pose. Called after the torso has turned: the arm is aimed from where its shoulder is
     * drawn.
     */
    public static void aimArms(UUID uuid, Function<String, ModelPart> parts) {
        State s = STATES.fresh(uuid);
        if (s == null || s.frame == null) return;
        boolean update = s.aimedAt != s.solvedAt;
        for (int i = 0; i < 2; i++) {
            boolean right = i == 0;
            ModelPart arm = parts.apply(right ? "right_arm" : "left_arm");
            if (arm == null) continue;
            boolean busy = occupied(s, right) || ownership(uuid, right) > 0.01f;
            WallPoseMath.Contact contact = s.contacts[i];
            if (update) {
                Touch touch = !busy && s.hands > 0.001f ? touch(s, arm, right) : null;
                s.touches[i] = touch;
                contact.update(touch == null ? null : touch.aim, s.hands, s.dt);
            }
            // A used/held hand belongs to that action immediately; clearance still turns the torso.
            if (!busy && contact.weight >= 0.001f) {
                arm.xRot = WallPoseMath.followAngle(arm.xRot, contact.pitch, contact.weight);
                arm.yRot = WallPoseMath.followAngle(arm.yRot, 0, contact.weight);
                arm.zRot = WallPoseMath.followAngle(arm.zRot, contact.roll, contact.weight);
            }
            if (update && EMFCompatConfig.getBoolean(KEY_TRACE, false)) {
                Touch touch = s.touches[i];
                float gap = Float.NaN;
                if (touch != null) {
                    Vector3f palm = new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot)
                            .transform(new Vector3f(0, PALM, 0)).add(arm.x, arm.y, arm.z);
                    gap = (float)(s.frame.jointWorld(palm).subtract(touch.face.position())
                            .dot(touch.face.normal()) / PIXEL - ARM_HALF);
                }
                LOGGER.info("[WallContactTrace] {} arm={} valid={} weight={} pitch={} roll={} gap={} turn={} side={}",
                        s.player.getName().getString(), right ? "R" : "L", touch != null,
                        contact.weight, arm.xRot, arm.zRot, gap, s.turn, s.side);
            }
        }
        s.aimedAt = s.solvedAt;
    }

    private record Touch(WallPoseMath.Aim aim, WallSurface.Hit face) {}

    private static Touch touch(State s, ModelPart arm, boolean right) {
        float toRight = arm.x + s.wallRight, toLeft = s.wallLeft - arm.x;
        // Keep each palm on its own side. With one wall missing, the far arm must not
        // reach across the torso just because the remaining wall is technically reachable.
        boolean onRight = right;
        float across = Math.max(0, (onRight ? toRight : toLeft) - ARM_HALF);
        if (across > WALL_REACH) return null;
        float along = (float)Math.sqrt(Math.max(0, PALM * PALM - across * across - PALM_BELOW * PALM_BELOW));
        boolean back = right == (s.side > 0);
        Vector3f pivot = new Vector3f(arm.x, arm.y, arm.z);
        // Confirm the face at the actual posed palm, including torso turn, crouch and foot lowering.
        // A shorter reach is useful while entering a doorway or passing a break in the wall.
        for (float extension : new float[]{1, 0.65f}) {
            float z = along * extension;
            float below = (float)Math.sqrt(Math.max(0, PALM * PALM - across * across - z * z));
            Vector3f target = new Vector3f(pivot).add(onRight ? -across : across, below, back ? z : -z);
            Vec3 palm = s.frame.jointWorld(target);
            Vector3f modelOut = s.frame.modelToWorld().transformDirection(new Vector3f(onRight ? -1 : 1, 0, 0));
            Vec3 out = new Vec3(modelOut.x, modelOut.y, modelOut.z).normalize();
            var face = WallSurface.clip(s.player, palm.subtract(out.scale(0.24)), palm.add(out.scale(0.24)), s.spaces);
            if (face == null || face.normal().dot(out) > -0.7) continue;
            Vec3 contact = face.position().add(face.normal().scale(ARM_HALF * PIXEL));
            Vector3f vector = s.frame.relativeToJoint(contact, pivot);
            if (Math.abs(vector.length() - PALM) > 0.8f) continue;
            var aim = WallPoseMath.aim(vector.x, vector.y, vector.z);
            if (aim != null) return new Touch(aim, face);
        }
        return null;
    }
}
