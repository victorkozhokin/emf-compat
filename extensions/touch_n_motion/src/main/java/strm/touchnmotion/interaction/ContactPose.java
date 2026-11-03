package strm.touchnmotion.interaction;

import strm.emfcompat.core.ik.IKMath;
/** Additive final rotations and pivot offsets, independent of the player's locomotion base. */
public final class ContactPose {
    private ContactPose() {}
    public static float[] follow(float[] previous, float[] target, float k) {
        float[] out = new float[6];
        for (int i = 0; i < 6; i++) {
            float difference = target[i] - previous[i];
            if (i < 3) difference = IKMath.wrap(difference);
            out[i] = previous[i] + difference * k;
        }
        return out;
    }
}
