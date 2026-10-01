package strm.emfcompat.animationadditions.buttonpress;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Quaternionf;

import java.util.function.Function;

/**
 * The reaching pose: past the arm's length the whole body goes with the hand - the torso leans
 * towards the target, the other arm swings back and out to balance it and the leg on the other
 * side lifts back off the ground, the weight on the leg under the reaching hand. For a lever or a
 * button ({@code ButtonPress}) and a hand crank ({@code BlockUse}); the torso's lean is asked of
 * {@code TorsoLean} by each.
 */
public final class ReachPose {

    /**
     * Reaching, in arm lengths from the shoulder. Further than {@link #FAR} only the hand points;
     * closer it goes over into the pose, all of it from {@link #FULL} (where a lean does get the
     * hand there) down to {@link #NEAR}, and out of it again by {@link #NONE}, where the arm
     * reaches on its own.
     */
    private static final float FAR = 2.4f;
    private static final float FULL = 1.8f;
    private static final float NEAR = 1.3f;
    private static final float NONE = 0.95f;
    /** The torso leans towards the target this much at most, radians... */
    private static final float LEAN = (float) Math.toRadians(25);
    /**
     * ...and this much more for a target below the waist, all of it {@link #LOW_FULL} pixels
     * under: a crank or a lever on the floor is out of reach of a torso leant 25 degrees.
     */
    private static final float LOW_LEAN = (float) Math.toRadians(25);
    private static final float LOW_FULL = 12f;
    /** How quickly the pose comes and goes, seconds. */
    public static final double SECONDS = 0.2;
    /** The torso turns round the waist, as {@code TorsoLean} does; model pixels. */
    private static final Vector3f WAIST = new Vector3f(0f, 12f, 0f);
    /**
     * For the right hand reaching, the left limbs, radians: the arm up out to its side and a little
     * back, the leg out and back. Mirrored for the left hand. Out, for a hanging left limb, is
     * -zRot (+zRot swings a hanging limb towards the model's right, -x); back is +xRot.
     */
    private static final float ARM_BACK = (float) Math.toRadians(20);
    private static final float ARM_OUT = (float) Math.toRadians(-20);
    private static final float LEG_BACK = (float) Math.toRadians(18);
    private static final float LEG_OUT = (float) Math.toRadians(-10);

    private ReachPose() {
    }

    /** How much of the pose, 0..1, for a target {@code reach} arm lengths from the shoulder. */
    public static float weight(float reach) {
        return Math.min(ReachEnvelope.smooth((FAR - reach) / (FAR - FULL)),
                ReachEnvelope.smooth((reach - NONE) / (NEAR - NONE)));
    }

    /**
     * Adds the torso's lean towards {@code target} (model pixels) for {@code s} of the pose to
     * {@code lean} {pitch, yaw, roll}: forwards is -z, +xRot; to the right is -x, a roll to the
     * right +zRot.
     */
    public static void lean(Vector3f target, float s, float[] lean) {
        Vector3f to = new Vector3f(target).sub(WAIST);
        float flat = (float) Math.hypot(to.x, to.z);
        if (flat < 1e-3f || s <= 0f) return;
        // Model y is down: below the waist is +y.
        float amount = LEAN + LOW_LEAN * Mth.clamp(to.y / LOW_FULL, 0f, 1f);
        lean[0] += s * amount * (-to.z / flat);
        lean[2] += s * amount * (-to.x / flat);
    }

    /** Straighten the upper body around its actual waist, leaving feet and gameplay crouch intact. */
    public static void upright(Function<String, ModelPart> parts, float weight) {
        if (weight < 1e-3f) return;
        ModelPart body = parts.apply("body");
        if (body == null) return;
        float pitch = Math.min(Math.max(0, body.xRot), (float) Math.toRadians(20)) * weight;
        Vector3f waist = new Quaternionf().rotationZYX(body.zRot, body.yRot, body.xRot)
                .transform(new Vector3f(0, 12, 0)).add(body.x, body.y, body.z);
        Quaternionf turn = new Quaternionf().rotationX(-pitch);
        for (String name : new String[]{"body", "head", "hat", "right_arm", "left_arm"}) {
            ModelPart part = parts.apply(name);
            if (part == null) continue;
            Vector3f at = new Vector3f(part.x, part.y, part.z).sub(waist);
            turn.transform(at).add(waist);
            part.x = at.x;
            part.y = at.y - 4f * weight;
            part.z = at.z;
            // Preserve the player's gaze; hand aim runs after all torso movement.
            if (!name.equals("head") && !name.equals("hat")) part.xRot -= pitch;
        }
    }

    /** A bounded torso adjustment closes the remaining gap at the far part of a crank's orbit. */
    public static void contact(Function<String, ModelPart> parts, boolean right, Vector3f target, float weight, Quaternionf smoothed, double dt) {
        ModelPart body = parts.apply("body");
        ModelPart arm = parts.apply(right ? "right_arm" : "left_arm");
        if (body == null || arm == null) return;
        Vector3f waist = new Quaternionf().rotationZYX(body.zRot, body.yRot, body.xRot)
                .transform(new Vector3f(0, 12, 0)).add(body.x, body.y, body.z);
        Quaternionf wanted = new Quaternionf().slerp(ReachEnvelope.contactTurn(
                new Vector3f(arm.x, arm.y, arm.z).sub(waist), new Vector3f(target).sub(waist),
                11f, (float) Math.toRadians(25)), weight);
        Quaternionf turn = ReachEnvelope.followContact(smoothed, wanted, dt);
        for (String name : new String[]{"body", "head", "hat", "right_arm", "left_arm"}) {
            ModelPart part = parts.apply(name);
            if (part == null) continue;
            Vector3f at = turn.transform(new Vector3f(part.x, part.y, part.z).sub(waist)).add(waist);
            part.setPos(at.x, at.y, at.z);
            if (!name.equals("head") && !name.equals("hat")) {
                Vector3f angles = new Quaternionf(turn).mul(new Quaternionf()
                        .rotationZYX(part.zRot, part.yRot, part.xRot)).getEulerAnglesZYX(new Vector3f());
                part.setRotation(angles.x, angles.y, angles.z);
            }
        }
    }

    /** Blends the balancing limbs in by {@code s}, 0..1, for the {@code right} (or left) hand reaching. */
    public static void balance(Function<String, ModelPart> parts, boolean right, float s, boolean otherArmFree) {
        balance(parts, right, s, otherArmFree, true);
    }

    public static void balance(Function<String, ModelPart> parts, boolean right, float s, boolean otherArmFree, boolean liftLeg) {
        if (s < 1e-3f) return;
        float side = right ? 1f : -1f;
        ModelPart arm = parts.apply(right ? "left_arm" : "right_arm");
        if (arm != null && otherArmFree) {
            arm.xRot += ARM_BACK * s;
            arm.zRot += side * ARM_OUT * s;
        }
        ModelPart leg = parts.apply(right ? "left_leg" : "right_leg");
        if (leg != null && liftLeg) {
            leg.xRot += LEG_BACK * s;
            leg.zRot += side * LEG_OUT * s;
        }

    }
}
