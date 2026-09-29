package strm.emfcompat.animationadditions.buttonpress;

import net.minecraft.client.model.geom.ModelPart;

import java.util.function.Function;

/**
 * The body going with a hand that reaches far - for a lever, for a trapdoor: the other arm swings
 * back and out to balance it and the leg on the other side lifts back off the ground, the weight
 * on the leg under the reaching hand. The torso's lean itself is {@code TorsoLean}'s, asked for
 * by each feature.
 */
public final class ReachPose {

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
