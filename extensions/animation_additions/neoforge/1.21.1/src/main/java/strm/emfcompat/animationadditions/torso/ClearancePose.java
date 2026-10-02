package strm.emfcompat.animationadditions.torso;

/** Clearance release is driven by the stance, never by the animated stride. */
final class ClearancePose {
    private ClearancePose() {}

    static float lift(float clearance, float crouch) {
        // A moving FA thigh must not remove the clearance restored at rest.
        return 3.25f * Math.max(0, Math.min(1, clearance))
                * Math.max(0, Math.min(1, crouch));
    }
}
