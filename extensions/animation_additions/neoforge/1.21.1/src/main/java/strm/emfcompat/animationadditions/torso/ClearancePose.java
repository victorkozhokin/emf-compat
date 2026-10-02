package strm.emfcompat.animationadditions.torso;

/** Bounds the extra compression of an already crouched pose during wall clearance. */
final class ClearancePose {
    private ClearancePose() {}

    static float lift(float torsoBottomY, float hipsY, float clearance, float crouch) {
        // The authored squat is retained. Only the excess overlap activated by
        // obstacle clearance is relieved, with a small upward release and a bounded overlap correction.
        float overlap = Math.max(0, torsoBottomY - hipsY - 3);
        return (.75f + Math.min(2.5f, overlap)) * Math.max(0, Math.min(1, clearance))
                * Math.max(0, Math.min(1, crouch));
    }
}
