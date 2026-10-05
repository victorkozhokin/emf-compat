package strm.emfcompat.animationadditions.torso;

import strm.emfcompat.animationadditions.interaction.Skeleton;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.buttonpress.ButtonPress;
import strm.emfcompat.animationadditions.blockuse.BlockUse;
import strm.emfcompat.animationadditions.ejector.EjectorLaunch;
import strm.emfcompat.animationadditions.footgrounding.compat.FootGrounding;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.animationadditions.motion.MotionRuntime;
import strm.emfcompat.animationadditions.wallhand.WallSqueeze;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.EMFCompatCore;

import java.util.UUID;
import java.util.function.Function;

/**
 * The torso follows the rest a little: it turns part of the way after the head (a look at a
 * creature) and leans and shifts over the foot that carries the weight, forwards onto a step.
 *
 * <p>It turns round the waist. In the player model the head, the arms and the torso are siblings,
 * not children of one another, so turning the torso alone would tear them off: their pivots are
 * carried round the waist with it and they get the same turn. The legs stay. Applied before the
 * arm aims, so a hand on a wall aims from where the shoulder really is.</p>
 *
 * <p>Everything is small on purpose - a few degrees, a pixel - and smoothed.</p>
 */
public final class TorsoLean {

    public static final String KEY_ENABLED = "torso.enabled";
    public static final String KEY_MOTION = "torso.motion";

    /** Share of the head's turn the torso takes, and the most it turns. */
    private static final float FOLLOW_YAW = 0.3f;
    private static final float FOLLOW_PITCH = 0.1f;
    private static final float MAX_YAW = (float) Math.toRadians(20);
    /** Over the foot with the weight: sideways shift (model pixels) and roll. */
    private static final float SHIFT = 1.0f;
    private static final float ROLL = (float) Math.toRadians(4);
    /** Forwards over a foot up on a step, and over a foot reaching for one. */
    private static final float CLIMB_PITCH = (float) Math.toRadians(8);
    private static final float REACH_PITCH = (float) Math.toRadians(5);
    private static final double SECONDS = 0.12;
    /**
     * Leaning with the motion: forwards speeding up and back braking, degrees per block/s^2, and
     * into a turn, degrees per (radian/s * block/s); each at most MOTION_MAX.
     */
    private static final float ACCEL_PITCH = (float) Math.toRadians(0.35);
    private static final float TURN_ROLL = (float) Math.toRadians(0.4);
    private static final float MOTION_MAX = (float) Math.toRadians(7);
    /** The waist, where the torso turns: the bottom of the 12 px torso below the neck pivot. */
    private static final float WAIST = Skeleton.WAIST.y;

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);
    private static final String[] CARRIED = {"head", "hat", "right_arm", "left_arm"};

    private TorsoLean() {
    }

    private static final class State {
        /** {pitch, yaw, roll, shift x, the part of the yaw the head stays out of} as shown, smoothed. */
        final float[] lean = new float[5];
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Torso lean", true,
                "On", "The torso turns a little after the head and leans over the foot that carries the weight.",
                "Off", "Leave the torso to EMF.");
        config.addBoolean(KEY_MOTION, "Lean with the motion", true,
                "On", "The torso leans forwards speeding up, back braking, and into a turn.",
                "Off", "No lean from how the player moves.");
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    /** Called right before the model is animated, after the feet and the interactions have solved. */
    public static void modelPose(AbstractClientPlayer player) {
        UUID uuid = player.getUUID();
        long now = System.nanoTime();
        EntityStates.Entry<State> entry = STATES.seen(uuid, now);
        double dt = EntityStates.due(entry, now);
        if (dt < 0) return;
        float[] target = new float[5];
        boolean on = isEnabled() && EMFCompatCore.isCompatEnabled()
                && !EMFCompatCore.isLocalPlayerInFirstPerson(uuid);
        if (on) {
            float[] head = InteractionRuntime.aim(uuid, Effector.HEAD);
            if (head != null) {
                target[0] += head[0] * FOLLOW_PITCH * head[2];
                target[1] += Math.max(-MAX_YAW, Math.min(MAX_YAW, head[1] * FOLLOW_YAW)) * head[2];
            }
            for (float[] press : new float[][]{ButtonPress.torsoHint(uuid), BlockUse.torsoHint(uuid), EjectorLaunch.torsoHint(uuid), WallSqueeze.torsoHint(uuid)}) {
                if (press == null) continue;
                target[0] += press[0];
                target[1] += press[1];
                target[2] += press[2];
                // A hint may also shift the torso, and say how much of its yaw the head stays out of.
                if (press.length > 3) target[3] += press[3];
                if (press.length > 4) target[4] += press[4];
            }
            if (EMFCompatConfig.getBoolean(KEY_MOTION, true)) {
                MotionRuntime.Motion m = MotionRuntime.get(uuid);
                // Speeding up forwards is +xRot (forwards); a turn to the right leans right, +zRot.
                target[0] += clamp(m.accelForward() * ACCEL_PITCH);
                target[2] += clamp(m.turnRate() * m.speed() * TURN_ROLL);
            }
            float[] feet = FootGrounding.torsoHint(uuid);
            if (feet != null) {
                // Leaning forward is +xRot (as the vanilla crouch); over the left foot (+x) the torso rolls left (-zRot).
                target[0] += feet[1] * CLIMB_PITCH + feet[2] * REACH_PITCH;
                target[2] -= feet[0] * ROLL;
                target[3] += feet[0] * SHIFT;
            }
        }
        float k = Smoothing.snapFirst(dt, SECONDS);
        float[] lean = entry.value.lean;
        for (int i = 0; i < 5; i++) lean[i] += (target[i] - lean[i]) * k;
    }

    private static float clamp(float v) {
        return Math.max(-MOTION_MAX, Math.min(MOTION_MAX, v));
    }

    /** Turns the torso, and carries the head and the arms with it. Called after the pack has animated. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null) return;
        float[] lean = state.lean;
        if (Math.abs(lean[0]) + Math.abs(lean[1]) + Math.abs(lean[2]) + Math.abs(lean[3]) < 1e-4f) return;
        ModelPart body = parts.apply("body");
        if (body == null) return;
        Quaternionf turn = new Quaternionf().rotationZYX(lean[2], lean[1], lean[0]);
        Vector3f waist = new Vector3f(body.x, body.y + WAIST, body.z);
        carry(body, turn, waist, lean);
        for (String name : CARRIED) {
            ModelPart part = parts.apply(name);
            if (part == null) continue;
            carry(part, turn, waist, lean);
            // Turned to fit a gap, the head keeps looking where it looked.
            if (name.equals("head") || name.equals("hat")) part.yRot -= lean[4];
        }
    }

    /** Moves a part's pivot round the waist by {@code turn}, shifts it, and adds the turn to it. */
    private static void carry(ModelPart part, Quaternionf turn, Vector3f waist, float[] lean) {
        Vector3f pivot = new Vector3f(part.x, part.y, part.z).sub(waist);
        turn.transform(pivot).add(waist);
        part.x = pivot.x + lean[3];
        part.y = pivot.y;
        part.z = pivot.z;
        // Small turns: adding them to the part's own is close enough.
        part.xRot += lean[0];
        part.yRot += lean[1];
        part.zRot += lean[2];
    }
}
