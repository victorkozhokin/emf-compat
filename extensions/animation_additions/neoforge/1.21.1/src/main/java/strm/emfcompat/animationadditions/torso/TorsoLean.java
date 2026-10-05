package strm.emfcompat.animationadditions.torso;

import strm.emfcompat.animationadditions.interaction.Skeleton;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * carried round the waist with it and they get the same turn. Obstacle clearance also carries
 * the hips a little while retaining the solved soles. Applied before the
 * arm aims, so a hand on a wall aims from where the shoulder really is.</p>
 *
 * <p>Everything is small on purpose - a few degrees, a pixel - and smoothed.</p>
 */
public final class TorsoLean {
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatTorso");

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
    /** Vanilla torso length, used only as a fallback if animated leg roots are unavailable. */
    private static final float WAIST = Skeleton.WAIST.y;

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);
    private static final String[] CARRIED = {"head", "hat", "right_arm", "left_arm"};

    private TorsoLean() {
    }

    /**
     * What a feature asks of the torso: a turn round the waist, radians; a shift to the side,
     * pixels; and how much of the yaw the head stays out of, radians - turned to fit a gap, the
     * head keeps looking where it looked.
     */
    public record Hint(float pitch, float yaw, float roll, float shift, float headStaysOut) {
        public static Hint turn(float pitch, float yaw, float roll) {
            return new Hint(pitch, yaw, roll, 0f, 0f);
        }

        /** A hint still given as {pitch, yaw, roll[, shift[, the yaw the head stays out of]]}; {@code null} stays none. */
        public static Hint of(float[] hint) {
            if (hint == null) return null;
            return new Hint(hint[0], hint[1], hint[2], hint.length > 3 ? hint[3] : 0f, hint.length > 4 ? hint[4] : 0f);
        }
    }

    private static final class State {
        /** {pitch, yaw, roll, shift x, the part of the yaw the head stays out of} as shown, smoothed. */
        final float[] lean = new float[5];
        float wallYaw, wallShift, legShare, crouch;
        final ClearanceOffset clearanceOffset = new ClearanceOffset();
        final ClearanceOffset contactBody = new ClearanceOffset(true);
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Torso lean", true,
                "On", "The torso turns a little after the head and leans over the foot that carries the weight.",
                "Off", "Leave the torso to EMF.");
        config.addChild(KEY_ENABLED, KEY_MOTION, "Lean with the motion", true,
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
            for (Hint hint : new Hint[]{ButtonPress.torsoHint(uuid), Hint.of(strm.emfcompat.animationadditions.buttonpress.HeavyThrottle.torsoHint(uuid)), BlockUse.torsoHint(uuid), EjectorLaunch.torsoHint(uuid), WallSqueeze.torsoHint(uuid), Hint.of(strm.emfcompat.animationadditions.transport.TransportGrip.torsoHint(uuid)), Hint.of(FootGrounding.terrainHint(uuid)), Hint.of(strm.emfcompat.animationadditions.leash.LeashHold.torsoHint(uuid)), strm.emfcompat.animationadditions.pocket.PocketStash.torsoHint(uuid), strm.emfcompat.animationadditions.mining.Mining.torsoHint(uuid), strm.emfcompat.animationadditions.gesture.Gesture.torsoHint(uuid)}) {
                if (hint == null) continue;
                target[0] += hint.pitch;
                target[1] += hint.yaw;
                target[2] += hint.roll;
                target[3] += hint.shift;
                target[4] += hint.headStaysOut;
            }
            if (EMFCompatConfig.getBoolean(KEY_MOTION, true)) {
                MotionRuntime.Motion m = MotionRuntime.get(uuid);
                // Speeding up forwards is +xRot (forwards); a turn to the right leans right, +zRot.
                target[0] += clamp(m.accelForward() * ACCEL_PITCH);
                target[2] += clamp(m.turnRate() * m.speed() * TURN_ROLL);
            }
            FootGrounding.Weight feet = FootGrounding.torsoHint(uuid);
            if (feet != null) {
                // Leaning forward is +xRot (as the vanilla crouch); over the left foot (+x) the torso rolls left (-zRot).
                target[0] += feet.climb() * CLIMB_PITCH + feet.reach() * REACH_PITCH;
                target[2] -= feet.side() * ROLL;
                target[3] += feet.side() * SHIFT;
            }
        }
        float k = Smoothing.snapFirst(dt, SECONDS);
        float[] lean = entry.value.lean;
        for (int i = 0; i < 5; i++) lean[i] += (target[i] - lean[i]) * k;
        Hint wall = on ? WallSqueeze.torsoHint(uuid) : null;
        State state = entry.value;
        state.wallYaw += ((wall == null ? 0 : wall.yaw()) - state.wallYaw) * k;
        state.wallShift += ((wall == null ? 0 : wall.shift()) - state.wallShift) * k;
        float share = player.isCrouching() ? 0.35f : 0.15f;
        state.legShare += (share - state.legShare) * k;
        state.crouch += ((player.isCrouching() ? 1 : 0) - state.crouch) * k;
    }

    private static float clamp(float v) {
        return Math.max(-MOTION_MAX, Math.min(MOTION_MAX, v));
    }

    /** Turns the torso, and carries the head and the arms with it. Called after the pack has animated. */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        // Seated cockpit motion comes from the deck, not the player's free camera yaw.
        if(strm.emfcompat.animationadditions.blockuse.CockpitControls.active(uuid))return;
        State state = STATES.fresh(uuid);
        if (state == null) return;
        float[] lean = state.lean;
        if (Math.abs(lean[0]) + Math.abs(lean[1]) + Math.abs(lean[2]) + Math.abs(lean[3]) < 1e-4f) return;
        ModelPart body = parts.apply("body");
        if (body == null) return;
        Quaternionf turn = new Quaternionf().rotationZYX(lean[2], lean[1], lean[0]);
        // The pack moves the leg roots independently when crouching. Their actual
        // centre is the pelvis; the geometric torso bottom is not its attachment.
        ModelPart right = parts.apply("right_leg"), left = parts.apply("left_leg");
        Vector3f waist = right != null && left != null
                ? new Vector3f((right.x + left.x) * 0.5f, (right.y + left.y) * 0.5f, (right.z + left.z) * 0.5f)
                : PelvisFollow.waist(new Vector3f(body.x, body.y, body.z),
                        body.xRot, body.yRot, body.zRot, WAIST * body.yScale);
        boolean trace = EMFCompatConfig.getBoolean(WallSqueeze.KEY_TRACE, false);
        Vector3f attachment = trace ? new Quaternionf().rotationZYX(body.zRot, body.yRot, body.xRot)
                .conjugate().transform(new Vector3f(waist).sub(body.x, body.y, body.z)) : null;
        Vector3f rightSole = trace && right != null ? sole(right) : null;
        Vector3f leftSole = trace && left != null ? sole(left) : null;
        for (String name : new String[]{"right_leg", "left_leg"}) {
            ModelPart leg = parts.apply(name);
            if (leg == null) continue;
            var follow = PelvisFollow.leg(new Vector3f(leg.x, leg.y, leg.z),
                    leg.xRot, leg.yRot, leg.zRot, 12 * leg.yScale, waist,
                    state.wallYaw * state.legShare, state.wallShift);
            leg.x = follow.pivot().x; leg.y = follow.pivot().y; leg.z = follow.pivot().z;
            leg.xRot = follow.pitch(); leg.yRot = follow.yaw(); leg.zRot = follow.roll();
        }
        // If leg reach limits some hip movement, keep the torso over the movement
        // actually achieved rather than shifting it away from the planted legs.
        float[] carriedLean = lean.clone();
        Vector3f carriedWaist = new Vector3f(waist);
        if (right != null && left != null) {
            Vector3f achieved = new Vector3f((right.x + left.x) * 0.5f,
                    (right.y + left.y) * 0.5f, (right.z + left.z) * 0.5f).sub(waist);
            carriedLean[3] += achieved.x - state.wallShift;
            carriedWaist.add(0, achieved.y, achieved.z);
        }
        float clearance = Math.min(1, Math.abs(state.wallYaw) / .5f + Math.abs(state.wallShift) / 1.5f);
        float relief = ClearancePose.lift(clearance, state.crouch);
        Vector3f retreat=WallSqueeze.crouchOffset(uuid, 3.5f*clearance*state.crouch);
        // Separate obstacle translation from the authored gait and other interactions.
        // The animated hip centre otherwise makes a steady wall yaw bob the chest
        // sharply on every stride; instantaneous reach correction adds another bob.
        Quaternionf withoutWall = new Quaternionf().rotationZYX(lean[2], lean[1]-state.wallYaw, lean[0]);
        Vector3f basePivot = PelvisFollow.carry(new Vector3f(body.x,body.y,body.z),
                body.xRot,body.yRot,body.zRot,withoutWall,waist,lean[3]-state.wallShift).pivot();
        carry(body, turn, waist, carriedLean);
        body.x+=retreat.x;body.z+=retreat.z;
        body.y += carriedWaist.y - waist.y - relief; body.z += carriedWaist.z - waist.z;
        Vector3f requestedOffset = new Vector3f(body.x,body.y,body.z).sub(basePivot);
        Vector3f correction = state.clearanceOffset.sample(
                traben.entity_model_features.models.animation.state.EMFState.getFrameCounter(),
                System.nanoTime(),requestedOffset).sub(requestedOffset);
        body.x+=correction.x;body.y+=correction.y;body.z+=correction.z;
        // The turned chest makes the pack's fast pelvis bob much more visible.
        // Give the contacted upper body inertia, while retaining the gait's legs
        // and the whole-model ground/stair translation.
        Vector3f pivot = new Vector3f(body.x,body.y,body.z);
        Vector3f contactRelease=state.contactBody.sample(
                traben.entity_model_features.models.animation.state.EMFState.getFrameCounter(),
                System.nanoTime(),pivot).sub(pivot).mul(clearance);
        body.x+=contactRelease.x;body.y+=contactRelease.y;body.z+=contactRelease.z;
        correction.add(contactRelease);
        if (trace) {
            Vector3f attached = new Quaternionf().rotationZYX(body.zRot, body.yRot, body.xRot)
                    .transform(attachment).add(body.x, body.y, body.z);
            Vector3f expected = new Vector3f(carriedWaist).add(carriedLean[3], -relief, 0).add(retreat).add(correction);
            float soleDrift = Math.max(rightSole == null ? 0 : rightSole.distance(sole(right)),
                    leftSole == null ? 0 : leftSole.distance(sole(left)));
            LOGGER.info("[PelvisTrace] wallYaw={} attachmentGap={} soleDrift={} crouchRelief={} hipDrop={} retreat={}",
                    state.wallYaw, attached.distance(expected), soleDrift, relief, carriedWaist.y - waist.y, retreat.z);
        }
        for (String name : CARRIED) {
            ModelPart part = parts.apply(name);
            if (part == null) continue;
            if (name.equals("head") || name.equals("hat")) {
                var carried = PelvisFollow.carryGaze(new Vector3f(part.x, part.y, part.z),
                        part.xRot, part.yRot, part.zRot, turn, waist, carriedLean[3]);
                part.setPos(carried.pivot().x, carried.pivot().y, carried.pivot().z);
            } else carry(part, turn, waist, carriedLean);
            part.x+=retreat.x+correction.x;part.y+=correction.y;part.z+=retreat.z+correction.z;
            part.y += carriedWaist.y - waist.y - relief; part.z += carriedWaist.z - waist.z;
        }
    }

    private static Vector3f sole(ModelPart leg) {
        return new Quaternionf().rotationZYX(leg.zRot, leg.yRot, leg.xRot)
                .transform(new Vector3f(0, 12 * leg.yScale, 0)).add(leg.x, leg.y, leg.z);
    }

    /** Moves a part's pivot round the waist by {@code turn}, shifts it, and adds the turn to it. */
    private static void carry(ModelPart part, Quaternionf turn, Vector3f waist, float[] lean) {
        var carried = PelvisFollow.carry(new Vector3f(part.x, part.y, part.z),
                part.xRot, part.yRot, part.zRot, turn, waist, lean[3]);
        part.x = carried.pivot().x; part.y = carried.pivot().y; part.z = carried.pivot().z;
        part.xRot = carried.pitch(); part.yRot = carried.yaw(); part.zRot = carried.roll();
    }
}
