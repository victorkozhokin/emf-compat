package strm.touchnmotion.pose;

import org.joml.Vector3f;

/**
 * What a motion asks of the body at one moment: where the hands go, how the head and the torso join
 * in, how the feet stand. Angles in radians, points in model pixels. A motion fills one of these each
 * frame - from code, or from authored key poses - and {@link PoseRunner} carries it out the same way
 * for all of them: the hands followed with a little give, the eyes leading, the feet stepping, the
 * torso fitted so a hand lands on its point.
 */
public class BodyPose {
    /** {xRot, yRot, zRot, the whole arm moved along y}; {@code null} leaves the arm alone. */
    public float[] right, left;
    /**
     * A point the hand goes to instead, from where the shoulder really is, and how far it has
     * got there from hanging by the side, 0..1. {@code onBody}: the point is a place on the
     * player's own body, given for an upright one, and goes with the shoulder as the torso bends.
     */
    public Vector3f rightAt, leftAt;
    public float rightReach, leftReach;
    public boolean onBody;
    /**
     * What the torso is fitted to, if not the hand that is further out: this arm's hand on this
     * point, this much, followed this slowly - for a motion whose hands come and go while the
     * body should make one move of it.
     */
    public Vector3f fitAt;
    public boolean fitRight;
    public float fitWeight, fitSeconds;
    /** {pitch, yaw} added to where the head looks. */
    public float[] head;
    /** A point the eyes go to, and how much of the way from where they look, 0..1. */
    public Vector3f look;
    public float looking;
    public float pitch, yaw, roll;
    /** Grounded weight transfer in model pixels, before solving the hands. */
    public float weightSide, weightForward;
    /** {xRot, yRot, zRot} added to a leg after the feet are set: a leg lifted, a foot shaken. */
    public float[] rightLeg, leftLeg;
    /** Where the soles stand from where the pack has them, while {@link #apart}. */
    public Vector3f rightFoot, leftFoot;
    public boolean apart;
    /** From this phase on the arms and the head are the pack's again. */
    public float letGo = .9f;
    /** Whether an arm with nothing to do joins in ({@link #free}): set by whoever runs the motion. */
    public boolean freeArms = true;

    public void hand(boolean right, Vector3f at, float reach) {
        if (right) {
            rightAt = at;
            rightReach = reach;
        } else {
            leftAt = at;
            leftReach = reach;
        }
    }

    /** The arm with nothing to do, out from the side and back a little, as an arm goes when the body leans the other way. */
    public void free(boolean right, float amount) {
        if (!freeArms) return;
        float[] angles = {.42f * amount, 0, (right ? .2f : -.2f) * amount, 0};
        if (right) this.right = angles;
        else left = angles;
    }

    /**
     * The same pose for the other side: what the right arm and leg were asked goes to the left ones and
     * the other way round, mirrored across the body - turns about y and z the other way, points to
     * the other side (x the other way). A motion is authored for the right hand and played for the
     * left by this, a left-handed player's main hand.
     */
    public void mirror() {
        float[] arm = right;
        right = mirrored(left);
        left = mirrored(arm);
        float[] leg = rightLeg;
        rightLeg = mirrored(leftLeg);
        leftLeg = mirrored(leg);
        Vector3f at = rightAt;
        rightAt = mirrored(leftAt);
        leftAt = mirrored(at);
        float reach = rightReach;
        rightReach = leftReach;
        leftReach = reach;
        Vector3f foot = rightFoot;
        rightFoot = mirrored(leftFoot);
        leftFoot = mirrored(foot);
        fitAt = mirrored(fitAt);
        fitRight = !fitRight;
        look = mirrored(look);
        if (head != null) head = new float[]{head[0], -head[1]};
        yaw = -yaw;
        roll = -roll;
        weightSide = -weightSide;
    }

    /** {xRot, yRot, zRot, ...} mirrored: the turns about y and z the other way, the rest as it was. */
    private static float[] mirrored(float[] angles) {
        if (angles == null) return null;
        float[] out = angles.clone();
        out[1] = -out[1];
        out[2] = -out[2];
        return out;
    }

    private static Vector3f mirrored(Vector3f point) {
        return point == null ? null : new Vector3f(-point.x, point.y, point.z);
    }

    /** Back to asking nothing, for the next frame. */
    public void reset() {
        right = left = head = rightLeg = leftLeg = null;
        rightAt = leftAt = rightFoot = leftFoot = look = fitAt = null;
        fitWeight = 0;
        fitSeconds = .14f;
        looking = 0;
        pitch = yaw = roll = weightSide = weightForward = 0;
        apart = onBody = false;
        rightReach = leftReach = 0;
        letGo = .9f;
    }
}
