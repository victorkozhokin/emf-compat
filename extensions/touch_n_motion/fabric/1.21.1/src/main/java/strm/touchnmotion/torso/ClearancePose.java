package strm.touchnmotion.torso;

import strm.touchnmotion.interaction.Ease;

/** Clearance release is driven by the stance, never by the animated stride. */
final class ClearancePose {
    private ClearancePose() {}

    static float lift(float clearance, float crouch) {
        // A moving FA thigh must not remove the clearance restored at rest.
        return 3.25f * Ease.unit(clearance)
                * Ease.unit(crouch);
    }
}
