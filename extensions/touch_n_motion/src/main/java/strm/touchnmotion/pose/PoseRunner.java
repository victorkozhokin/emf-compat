package strm.touchnmotion.pose;

import strm.emfcompat.core.ik.OneBoneIK;
import strm.emfcompat.core.ik.IKMath;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Vector3f;
import org.slf4j.Logger;
import strm.emfcompat.core.ik.IKFrame;
import strm.touchnmotion.interaction.Body;
import strm.touchnmotion.interaction.Ease;
import strm.touchnmotion.interaction.Effector;
import strm.emfcompat.core.ik.Skeleton;
import strm.touchnmotion.torso.BraceSteps;
import strm.touchnmotion.torso.LowReach;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Carries out a {@link BodyPose} for one player, the same for every motion that asks for one. In the
 * order of the draw:
 * <ol>
 *     <li>{@link #follow} and {@link #aims} - when the motion is asked: the hands' points followed with a
 *     little give and lifted in passing, the eyes turned into the head's aim, the arms' aims for the
 *     arbiter;</li>
 *     <li>{@link #support} - the feet stepped to the pose's stance and back, the weight put over them, a
 *     leg lifted;</li>
 *     <li>{@link #reach} - after the torso: the torso turned and the pelvis shifted until the hand that
 *     leads is on its point;</li>
 *     <li>{@link #aimArms} - after the runtime: a hand on a point aimed from where the shoulder is drawn.</li>
 * </ol>
 * The torso's own turn ({@link BodyPose#pitch} and the rest) is summed by whoever owns the motion, weighed
 * by how much of the body it has.
 */
public final class PoseRunner {
    /** As far round as a neck turns. */
    private static final float NECK = (float) Math.toRadians(70);
    private static final Vector3f HOME = new Vector3f();

    /** Where the hands are drawn to this frame: the pose's points, followed with a little give. */
    private final Vector3f[] hand = {new Vector3f(), new Vector3f()}, handSpeed = {new Vector3f(), new Vector3f()};
    private final boolean[] handSet = new boolean[2];
    /** The hands' points and the eyes' as last asked for at work, model pixels: what they come back from. */
    private final Vector3f[] kept = new Vector3f[3];
    private boolean keeps;
    /** The fit of the torso and the pelvis that brings the hand on to its point, and the hand and point it was last made for. */
    private final LowReach.State fit = new LowReach.State();
    private final Vector3f fitAt = new Vector3f();
    private boolean fitRight = true;
    /** The feet's steps to the stance and home. */
    public final BraceSteps.State stance = new BraceSteps.State();

    /** Nothing under way: no step of the feet, no fit of the torso still letting go. */
    public boolean resting() {
        return stance.resting() && !fit.active();
    }

    /** Remembers where the hands and the eyes are asked to be at work, to come back from there. */
    public void keep(BodyPose pose) {
        kept[0] = pose.rightAt == null ? null : new Vector3f(pose.rightAt);
        kept[1] = pose.leftAt == null ? null : new Vector3f(pose.leftAt);
        kept[2] = pose.look == null ? null : new Vector3f(pose.look);
        keeps = true;
    }

    /**
     * Coming back, the hands leave from where they were by the body - not from the thing they were at,
     * which a player who has turned away from it has behind them by now.
     */
    public void comeBack(BodyPose pose) {
        if (!keeps) return;
        pose.rightAt = kept[0] == null ? null : new Vector3f(kept[0]);
        pose.leftAt = kept[1] == null ? null : new Vector3f(kept[1]);
        pose.look = kept[2] == null ? null : new Vector3f(kept[2]);
    }

    /** Nothing kept to come back from. */
    public void forget() {
        keeps = false;
    }

    /** The hands' points this frame, after the pose's, over {@code dt} seconds. */
    public void follow(BodyPose pose, float dt) {
        pose.rightAt = follow(0, pose.rightAt, pose.rightReach, dt);
        pose.leftAt = follow(1, pose.leftAt, pose.leftReach, dt);
    }

    /**
     * The point a hand is drawn to: the pose's, followed by a spring a touch short of critical, so
     * a hand at work swings through its turns instead of tracking them, overshoots a hair and
     * settles; and lifted in passing, so the way out and back is an arc and not a line.
     */
    private Vector3f follow(int which, Vector3f at, float reach, float dt) {
        if (at == null) {
            handSet[which] = false;
            return null;
        }
        Vector3f now = hand[which], speed = handSpeed[which];
        if (!handSet[which]) {
            now.set(at);
            speed.zero();
            handSet[which] = true;
        } else {
            int steps = Math.max(1, (int) Math.ceil(dt / .008f));
            float h = dt / steps, stiff = 24f, damp = .8f;
            for (int i = 0; i < steps; i++) {
                // speed += (stiff² (at - now) - 2 damp stiff speed) h; now += speed h
                speed.x += (stiff * stiff * (at.x - now.x) - 2 * damp * stiff * speed.x) * h;
                speed.y += (stiff * stiff * (at.y - now.y) - 2 * damp * stiff * speed.y) * h;
                speed.z += (stiff * stiff * (at.z - now.z) - 2 * damp * stiff * speed.z) * h;
                now.fma(h, speed);
            }
        }
        float way = Ease.unit(reach);
        return new Vector3f(now).sub(0, 1.8f * 4 * way * (1 - way), 0);
    }

    /** The eyes go to the pose's point from where they look, and lead the hands there: their turn added to the head's. */
    public static void lookToHead(BodyPose pose, AbstractClientPlayer player) {
        if (pose.look == null) return;
        float length = pose.look.length(), amount = Ease.unit(pose.looking);
        if (length <= 1e-3f) return;
        float pitch = (float) Math.asin(Math.max(-1f, Math.min(1f, pose.look.y / length)));
        float yaw = (float) Math.atan2(-pose.look.x, -pose.look.z);
        float nowPitch = (float) Math.toRadians(player.getXRot()), nowYaw = Body.headYaw(player);
        float[] add = pose.head == null ? new float[2] : pose.head;
        pose.head = new float[]{add[0] + (pitch - nowPitch) * amount, add[1] + strm.emfcompat.core.ik.IKMath.wrap(yaw - nowYaw) * amount};
    }

    /** The arms' and the head's aims for the arbiter; empty when the pose asks only the body. */
    public static Map<Effector, float[]> aims(BodyPose pose, AbstractClientPlayer player) {
        Map<Effector, float[]> aims = new EnumMap<>(Effector.class);
        float[] r = arm(pose.right, pose.rightAt, pose.rightReach, Skeleton.RIGHT_SHOULDER);
        float[] l = arm(pose.left, pose.leftAt, pose.leftReach, Skeleton.LEFT_SHOULDER);
        if (r != null) aims.put(Effector.RIGHT_ARM, r);
        if (l != null) aims.put(Effector.LEFT_ARM, l);
        if (pose.head != null) {
            // The head leaves from where it looks and comes back to it, whatever the camera does meanwhile.
            float yaw = Body.headYaw(player);
            aims.put(Effector.HEAD, new float[]{(float) Math.toRadians(player.getXRot()) + pose.head[0],
                    Math.max(-NECK, Math.min(NECK, yaw + pose.head[1]))});
        }
        return aims;
    }

    /**
     * The aim of an arm from {@code pivot} at a point, {@code reach} of the way there from hanging.
     * The way is taken in the arm's angles, not along a line for the hand: a hand brought down
     * from the head along a line passes the shoulder itself and the arm whips round it.
     */
    private static float[] arm(float[] angles, Vector3f at, float reach, Vector3f pivot) {
        if (angles != null) return new float[]{angles[0], angles[1]};
        if (at == null) return null;
        float[] aim = towards(at.x - pivot.x, at.y - pivot.y, at.z - pivot.z);
        if (aim == null) return null;
        // A hair past is let through: the arm carries on a little and comes back.
        reach = Math.max(0, Math.min(1.1f, reach));
        return new float[]{aim[0] * reach, aim[1] * reach};
    }

    /**
     * {xRot, yRot} that point an arm that way. Which way round a hanging arm is turned says nothing
     * - nearly straight down, a point a pixel behind the shoulder would spin it half round - so the
     * turn is taken only as far as the point is out to a side.
     */
    private static float[] towards(float x, float y, float z) {
        IKMath.Angles aim = IKMath.anglesXY(x, y, z);
        if (aim == null) return null;
        return new float[]{aim.x(), aim.secondary() * Ease.smooth((float) Math.hypot(x, z) / 5f)};
    }

    /**
     * The feet, before the torso: a step each to the pose's stance, held, a step each back; the weight
     * over them and a leg lifted, as much as the motion shows.
     *
     * @param stanceOn whether the feet may step for it at all
     * @param shown    how much of the body the motion has now
     * @param twist    radians the hips turn against the shoulders over the feet as they stand
     */
    public void support(BodyPose pose, AbstractClientPlayer player, IKFrame frame, Function<String, ModelPart> parts,
                        boolean playing, boolean enabled, boolean stanceOn, float shown, float twist, Logger log, String id) {
        boolean apart = stanceOn && playing && pose.apart && pose.rightFoot != null && pose.leftFoot != null;
        float held = apart ? shown : playing && enabled && !stance.resting() ? 1 : shown;
        if (!(held < .05f && stance.resting()))
            BraceSteps.apply(stance, player, frame, parts, apart ? pose.rightFoot : HOME, apart ? pose.leftFoot : HOME, held, twist, log, id);
        if (!playing || shown < 1e-3f) return;
        if (stanceOn && Body.planted(player))
            strm.touchnmotion.torso.PelvisFollow.shift(parts, pose.weightSide * shown, pose.weightForward * shown);
        leg(parts.apply("right_leg"), pose.rightLeg, shown);
        leg(parts.apply("left_leg"), pose.leftLeg, shown);
    }

    private static void leg(ModelPart leg, float[] turn, float weight) {
        if (leg == null || turn == null) return;
        leg.xRot += turn[0] * weight;
        leg.yRot += turn[1] * weight;
        leg.zRot += turn[2] * weight;
    }

    /**
     * After the torso: an arm is as long as it is, so pointing it at a thing does not put the hand
     * on it - the torso turns about the hips and the pelvis shifts over the soles until the hand
     * that leads is on its point, the other hand's point weighed in when it is out as well.
     * Standing still only; a point on the player's own body needs none of it.
     *
     * @param fits  whether the torso may be fitted now: the motion shows, the option is on, the player stands
     */
    public void reach(BodyPose pose, Function<String, ModelPart> parts, boolean fits, float shown) {
        float weight = 0;
        Vector3f other = null;
        if (fits && !pose.onBody) {
            float r = pose.rightAt == null ? 0 : Math.min(1, pose.rightReach), l = pose.leftAt == null ? 0 : Math.min(1, pose.leftReach);
            // The hand further out leads; it gives the lead up only to one clearly further.
            if (fitRight ? l > r + .15f : r > l + .15f) fitRight = !fitRight;
            Vector3f at = fitRight ? pose.rightAt : pose.leftAt, second = fitRight ? pose.leftAt : pose.rightAt;
            if (pose.fitAt != null) {
                fitRight = pose.fitRight;
                fitAt.set(pose.fitAt);
                weight = shown * Ease.unit(pose.fitWeight);
            } else if (at != null) {
                fitAt.set(at);
                weight = shown * Ease.smooth((Math.max(r, l) - .5f) / .5f);
                if (second != null && Math.min(r, l) > .6f) other = second;
            }
        }
        if (fits && pose.fitAt != null) other = pose.fitOther;
        if (weight < 1e-3f && !fit.active()) return;
        // A moderate lean after the hand and no more: a thing further off is held out to, not lunged at.
        fit.angleLimit = pose.fitAngle;
        fit.followSeconds = pose.fitSeconds;
        fit.weightForward = pose.fitForward;
        LowReach.apply(parts, fitRight, fitAt, other, weight, fit);
    }

    /**
     * After the runtime: a hand that goes to a point goes there from the shoulder as it is drawn; the
     * roll and the lift put back. {@code right} and {@code left}: how much of each arm the motion has.
     */
    public static void aimArms(BodyPose pose, Function<String, ModelPart> parts, Map<Effector, float[]> base, float right, float left) {
        applyArm(parts.apply("right_arm"), pose.right, contact(parts, pose.rightAt, pose.onBody), pose.rightReach, pose.rightHold, pose.rightAdd,
                base == null ? null : base.get(Effector.RIGHT_ARM), right);
        applyArm(parts.apply("left_arm"), pose.left, contact(parts, pose.leftAt, pose.onBody), pose.leftReach, pose.leftHold, pose.leftAdd,
                base == null ? null : base.get(Effector.LEFT_ARM), left);
    }

    /** One arm: held to its point as the pose says, or, not held, swung free over the pack's as much as it is not the motion's. */
    private static void applyArm(ModelPart arm, float[] angles, Vector3f at, float reach, BodyPose.Hold hold, float[] add, float[] base, float weight) {
        if (arm == null) return;
        if (hold != BodyPose.Hold.POINT && at != null && weight >= .02f) {
            if (hold == BodyPose.Hold.FIST) OneBoneIK.turnTowards(arm, at, weight, true);
            else strm.touchnmotion.ride.BoatRide.settle(arm, at, weight);
            return;
        }
        if (add != null && (hold == BodyPose.Hold.POINT ? weight < 1e-3f : weight < .02f || at == null)) {
            float free = 1f - weight;
            arm.xRot += add[0] * free;
            arm.yRot += add[1] * free;
            arm.zRot += add[2] * free;
            return;
        }
        if (hold == BodyPose.Hold.POINT) one(arm, angles, at, reach, base, weight);
    }

    private static Vector3f contact(Function<String, ModelPart> parts, Vector3f at, boolean onBody) {
        if (at == null || !onBody) return at;
        ModelPart body = parts.apply("body"), r = parts.apply("right_leg"), l = parts.apply("left_leg");
        if (body == null || r == null || l == null) return at;
        return strm.touchnmotion.gesture.GestureMath.bodyPoint(at, strm.touchnmotion.torso.Stride.hips(r, l), body.xRot, body.yRot, body.zRot);
    }

    private static void one(ModelPart arm, float[] angles, Vector3f at, float reach, float[] base, float weight) {
        if (weight < 1e-3f) return;
        if (angles != null) {
            arm.zRot += (angles[2] - arm.zRot) * weight;
            arm.y += angles[3] * weight;
        } else if (at != null && base != null) {
            float[] aim = arm(null, at, reach, new Vector3f(arm.x, arm.y, arm.z));
            if (aim == null) return;
            // Replace the preliminary canonical-shoulder aim. Blend once from the post-torso base.
            float[] blended = strm.touchnmotion.interaction.ContactAim.blend(base, aim, weight);
            arm.setRotation(blended[0], blended[1], blended[2]);
        }
    }
}
