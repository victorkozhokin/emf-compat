package strm.emfcompat.animationadditions.buttonpress;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

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
    /** The torso leans towards the target this much at most, radians. */
    private static final float LEAN = (float) Math.toRadians(25);
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
    private static final float ARM_OUT = (float) Math.toRadians(-40);
    private static final float LEG_BACK = (float) Math.toRadians(35);
    private static final float LEG_OUT = (float) Math.toRadians(-20);

    private ReachPose() {
    }

    /** How much of the pose, 0..1, for a target {@code reach} arm lengths from the shoulder. */
    public static float weight(float reach) {
        return Math.min(Mth.clamp((FAR - reach) / (FAR - FULL), 0f, 1f),
                Mth.clamp((reach - NONE) / (NEAR - NONE), 0f, 1f));
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
        lean[0] += s * LEAN * (-to.z / flat);
        lean[2] += s * LEAN * (-to.x / flat);
    }

    /** Blends the balancing limbs in by {@code s}, 0..1, for the {@code right} (or left) hand reaching. */
    public static void balance(Function<String, ModelPart> parts, boolean right, float s) {
        if (s < 1e-3f) return;
        float side = right ? 1f : -1f;
        ModelPart arm = parts.apply(right ? "left_arm" : "right_arm");
        if (arm != null) {
            arm.xRot += (ARM_BACK - arm.xRot) * s;
            arm.zRot += (side * ARM_OUT - arm.zRot) * s;
        }
        ModelPart leg = parts.apply(right ? "left_leg" : "right_leg");
        if (leg != null) {
            leg.xRot += (LEG_BACK - leg.xRot) * s;
            leg.zRot += (side * LEG_OUT - leg.zRot) * s;
        }
        ModelPart stand = parts.apply(right ? "right_leg" : "left_leg");
        if (stand != null) stand.xRot += (0f - stand.xRot) * s;
    }
}
