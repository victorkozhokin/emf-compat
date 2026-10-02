package strm.emfcompat.animationadditions.torso;

/** Bounds the extra compression of an already crouched pose during wall clearance. */
final class ClearancePose {
    private ClearancePose() {}

    static float lift(float torsoBottomY, float hipsY, float clearance, float crouch) {
        // The authored squat is retained. Only the excess overlap activated by
        // obstacle clearance is relieved, by at most two model pixels.
        float overlap = Math.max(0, torsoBottomY - hipsY - 3);
        return Math.min(2, overlap) * Math.max(0, Math.min(1, clearance))
                * Math.max(0, Math.min(1, crouch));
    }
}
