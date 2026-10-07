package strm.emfcompat.animationadditions.motion;

import strm.emfcompat.animationadditions.DebugLog;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.animationadditions.interaction.EntityStates;

import java.util.UUID;

/**
 * How a player is moving, as smooth continuous signals for the motion layers (the torso's lean,
 * the sprint stop, ...): speed forwards and sideways in the body's own frame, how fast that
 * changes, how fast the body turns, how long on the ground or in the air.
 *
 * <p>Measured per game tick - the position a tick apart - so drawing the player twice in a frame,
 * or at any frame rate, gives the same numbers; only the smoothing runs per frame. Works the same
 * for the local player and anyone watched: it reads nothing but the synced position and body yaw.</p>
 */
public final class MotionRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatMotion");
    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    /** Half-lives, seconds: speed follows quickly, acceleration and turning a little slower. */
    private static final double SPEED_HALFLIFE = 0.05;
    private static final double ACCEL_HALFLIFE = 0.08;
    private static final double TURN_HALFLIFE = 0.08;

    private MotionRuntime() {
    }

    /** The signals, all in blocks and seconds; forward and right are the body's. */
    public record Motion(float forward, float right, float vertical, float speed,
                         float accelForward, float accelRight, float turnRate,
                         float groundTime, float airTime, boolean onGround, boolean sprinting) {
        public static final Motion STILL = new Motion(0, 0, 0, 0, 0, 0, 0, 0, 0, true, false);
    }

    private static final class State {
        int tick = Integer.MIN_VALUE;
        float rawForward, rawRight, rawAccelForward, rawAccelRight, rawTurn, rawVertical;
        float lastYaw;
        boolean onGround = true, sprinting;
        float groundTime, airTime;
        final Spring forward = new Spring(), right = new Spring(), vertical = new Spring();
        final Spring accelForward = new Spring(), accelRight = new Spring(), turn = new Spring();
        Motion motion = Motion.STILL;
        final DebugLog.Pace tracePace = new DebugLog.Pace();
    }

    /** Called right before the model is animated. */
    public static void modelPose(AbstractClientPlayer player) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        EntityStates.Entry<State> entry = STATES.seen(uuid, now);
        double dt = EntityStates.due(entry, now);
        if (dt < 0) return;
        State s = entry.value;

        if (player.tickCount != s.tick) {
            boolean first = s.tick == Integer.MIN_VALUE || player.tickCount - s.tick > 5;
            float yaw = player.yBodyRot;
            double rad = Math.toRadians(yaw);
            // Minecraft yaw: 0 faces +z; forward (-sin, cos), the right of it (-cos, -sin).
            double dx = (player.getX() - player.xo) * 20, dz = (player.getZ() - player.zo) * 20;
            float forward = (float) (-Math.sin(rad) * dx + Math.cos(rad) * dz);
            float right = (float) (-Math.cos(rad) * dx - Math.sin(rad) * dz);
            float ticks = first ? 1 : Math.max(1, player.tickCount - s.tick);
            s.rawAccelForward = first ? 0 : (forward - s.rawForward) * 20 / ticks;
            s.rawAccelRight = first ? 0 : (right - s.rawRight) * 20 / ticks;
            // Degrees a tick turned into radians a second; + is a turn to the right.
            s.rawTurn = first ? 0 : (float) Math.toRadians(Mth.wrapDegrees(yaw - s.lastYaw) * 20 / ticks);
            s.rawForward = forward;
            s.rawRight = right;
            s.rawVertical = (float) ((player.getY() - player.yo) * 20);
            s.lastYaw = yaw;
            s.onGround = player.onGround();
            s.sprinting = player.isSprinting();
            s.tick = player.tickCount;
        }
        if (s.onGround) {
            s.groundTime += (float) dt;
            s.airTime = 0;
        } else {
            s.airTime += (float) dt;
            s.groundTime = 0;
        }
        if (dt == 0) {
            s.forward.set(s.rawForward);
            s.right.set(s.rawRight);
            s.vertical.set(s.rawVertical);
        }
        s.forward.update(s.rawForward, SPEED_HALFLIFE, dt);
        s.right.update(s.rawRight, SPEED_HALFLIFE, dt);
        s.vertical.update(s.rawVertical, SPEED_HALFLIFE, dt);
        s.accelForward.update(s.rawAccelForward, ACCEL_HALFLIFE, dt);
        s.accelRight.update(s.rawAccelRight, ACCEL_HALFLIFE, dt);
        s.turn.update(s.rawTurn, TURN_HALFLIFE, dt);
        s.motion = new Motion(s.forward.value, s.right.value, s.vertical.value,
                (float) Math.hypot(s.forward.value, s.right.value),
                s.accelForward.value, s.accelRight.value, s.turn.value,
                s.groundTime, s.airTime, s.onGround, s.sprinting);

        if (DebugLog.trace() && s.tracePace.due(100_000_000L)) {
            Motion m = s.motion;
            LOGGER.info("[Motion] {} fwd {} right {} up {} accF {} accR {} turn {} {} {}s",
                    player.getName().getString(), f(m.forward), f(m.right), f(m.vertical),
                    f(m.accelForward), f(m.accelRight), f(m.turnRate),
                    m.onGround ? "ground" : "air", f(m.onGround ? m.groundTime : m.airTime));
        }
    }

    private static String f(float v) {
        return String.format("%.2f", v);
    }

    /** The player's motion this frame; standing still when not drawn lately. */
    public static Motion get(UUID uuid) {
        State s = STATES.fresh(uuid);
        return s == null ? Motion.STILL : s.motion;
    }
}
