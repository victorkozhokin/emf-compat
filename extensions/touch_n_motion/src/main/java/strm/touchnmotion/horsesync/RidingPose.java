package strm.touchnmotion.horsesync;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Our own horse-riding pose for the player: a forward-leaning "jockey" seat — the torso and the
 * hands on the reins tip forward over the horse's neck as one unit, the head carried with them still
 * looking where the rider looks, while the legs grip the sides, with a small looping bob driven by
 * the horse's gait (still when it stands, bobs when it moves).
 *
 * <p>The {@code crouch} parameter tilts the upper body forward <em>about the hip</em> as a rigid
 * body — the body and arms are rotated and repositioned, the head only repositioned, so they stay joined at the seat
 * (rotating a single part about its own neck pivot would detach it from the hips). The legs are
 * left out of the crouch so they keep hugging the barrel.</p>
 *
 * <p>Axis notes: negative
 * {@code xRot} = forward for arms; body/head pivot at the neck so positive {@code crouch}/
 * {@code bodyLean} lean forward; legs are spread by position, not rotation.</p>
 */
public final class RidingPose {

    /** Model-space Y of the hip / seat that the upper body pivots around. */
    private static final float HIP_Y = 12.0f;
    private static final float ARM_BASE_X = 5.0f;
    private static final float ARM_BASE_Y = 2.0f;

    // Legs: grip the sides (spread by position + roll), hanging straight down.
    private static final float LEG_PITCH = 0.0f;
    private static final float LEG_BASE_X = 1.9f;
    private static final float LEG_SPREAD_POS = 1.0f;
    private static final float LEG_ROLL = -0.50f;
    // Arms: forward and inward on the reins.
    private static final float ARM_PITCH = -0.85f;
    private static final float ARM_YAW = 0.12f;
    private static final float ARM_ROLL = -0.10f;
    // Torso-only micro-lean (on top of the crouch).
    private static final float BODY_LEAN = 0.10f;
    // Whole-upper-body forward lean about the hip (the gallop seat).
    private static final float CROUCH = 0.30f;
    // Gait-driven bob.
    private static final float GAIT_FREQ = 0.70f;
    private static final float MOVE_GAIN = 2.0f;
    private static final float LEG_BOB = 0.15f;
    private static final float ARM_BOB = 0.08f;
    private static final float BODY_BOB = 0.04f;

    private RidingPose() {
    }

    /**
     * Writes the riding pose onto the model parts.
     *
     * @param horseLimbSwing  the horse's accumulated limb-swing position (drives the bob phase)
     * @param horseLimbSpeed  the horse's limb-swing speed (0 when standing still)
     * @param upperBody       when {@code false}, only the leg seat is posed and the arms/torso/head
     *                        are left for an active action pose (gun aim, melee swing) to control
     * @param rightOnReins    whether the right hand is on the reins; a hand in use is left as the game posed it
     * @param leftOnReins     the same for the left hand
     * @param upright         0 leant over the neck, 1 sat up - while a hand is off the reins, so the
     *                        arm the game poses at a standing shoulder stays on it
     */
    public static void apply(net.minecraft.client.model.HumanoidModel<?> model, float horseLimbSwing, float horseLimbSpeed, boolean upperBody,
                             boolean rightOnReins, boolean leftOnReins, float upright) {
        float move = Mth.clamp(horseLimbSpeed * MOVE_GAIN, 0.0f, 1.0f);
        float bob = Mth.sin(horseLimbSwing * GAIT_FREQ) * move;

        // Legs grip the sides — always posed, even while an action controls the upper body.
        float legX = LEG_BASE_X + LEG_SPREAD_POS;
        float legPitch = LEG_PITCH + bob * LEG_BOB;
        float legRoll = LEG_ROLL;

        ModelPart rightLeg = model.rightLeg;
        rightLeg.x = -legX;
        rightLeg.xRot = legPitch;
        rightLeg.yRot = 0.0f;
        rightLeg.zRot = -legRoll;

        ModelPart leftLeg = model.leftLeg;
        leftLeg.x = legX;
        leftLeg.xRot = legPitch;
        leftLeg.yRot = 0.0f;
        leftLeg.zRot = legRoll;

        // Upper body (arms/torso/head/crouch) is skipped while an action pose owns the arms.
        if (!upperBody) {
            return;
        }
        float lean = 1.0f - upright;

        // Arm base rotation (reins). Crouch adds to this below.
        float armPitch = ARM_PITCH + bob * ARM_BOB;
        ModelPart rightArm = model.rightArm;
        if (rightOnReins) {
            rightArm.xRot = armPitch;
            rightArm.yRot = ARM_YAW;
            rightArm.zRot = ARM_ROLL;
        }
        ModelPart leftArm = model.leftArm;
        if (leftOnReins) {
            leftArm.xRot = armPitch;
            leftArm.yRot = -ARM_YAW;
            leftArm.zRot = -ARM_ROLL;
        }

        // Torso base rotation (micro-lean). Crouch adds to this below.
        model.body.xRot = (BODY_LEAN + bob * BODY_BOB) * lean;
        model.body.yRot = 0.0f;
        model.body.zRot = 0.0f;

        // Gallop crouch: tilt the body and the hands on the reins forward about the hip, as one rigid
        // unit, so they stay joined at the seat. The head goes with the neck but keeps looking where
        // the rider looks.
        float crouch = CROUCH * lean;
        leanAboutHip(model.body, 0.0f, 0.0f, crouch, true);
        leanAboutHip(model.head, 0.0f, 0.0f, crouch, false);
        if (rightOnReins) leanAboutHip(rightArm, -ARM_BASE_X, ARM_BASE_Y, crouch, true);
        if (leftOnReins) leanAboutHip(leftArm, ARM_BASE_X, ARM_BASE_Y, crouch, true);
    }

    /**
     * Rotates {@code part} forward by {@code a} radians about the hip point {@code (0, HIP_Y, 0)},
     * repositioning its pivot so the upper body tilts as a rigid unit instead of detaching. The
     * part's own {@code xRot} is added to (not overwritten) when it turns with the lean.
     *
     * @param baseX the part's default pivot X offset
     * @param baseY the part's default pivot Y offset
     * @param turn  whether the part turns with the lean, or only its pivot moves (the head, which
     *              keeps the look)
     */
    private static void leanAboutHip(ModelPart part, float baseX, float baseY, float a, boolean turn) {
        float cos = Mth.cos(a);
        float sin = Mth.sin(a);
        float dy = baseY - HIP_Y;
        part.x = baseX;
        part.y = HIP_Y + dy * cos;
        part.z = dy * sin;
        if (turn) part.xRot += a;
    }
}
