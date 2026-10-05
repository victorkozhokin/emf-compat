package strm.emfcompat.animationadditions.footgrounding.compat;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The pack may raise a crouched hip independently of the renderer's crouch translation. */
final class SoleContact {
    static float height(float[] pose) {
        if (pose == null || pose[6] <= 0) return Float.NaN;
        Vector3f sole = new Quaternionf().rotationZYX(pose[2], pose[1], pose[0])
                .transform(new Vector3f(0, 12 * pose[6], 0));
        return pose[4] + sole.y;
    }

    static float lowering(float[] right, float[] left) {
        float r = height(right), l = height(left);
        if (!Float.isFinite(r) || !Float.isFinite(l)) return 0;
        // Keep the lower animated sole planted; the other retains the pack's lift/swing.
        return Math.max(0, Math.min(4, 24 - Math.max(r, l)));
    }
}
