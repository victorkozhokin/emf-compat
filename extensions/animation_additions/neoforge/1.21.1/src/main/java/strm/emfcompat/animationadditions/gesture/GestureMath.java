package strm.emfcompat.animationadditions.gesture;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

/** Contact geometry and equipment events independent of the render clock. */
final class GestureMath {
    private GestureMath() {}
    static Vector3f bodyPoint(Vector3f upright, Vector3f hips, float pitch, float yaw, float roll) {
        return new Quaternionf().rotationZYX(roll, yaw, pitch)
                .transform(new Vector3f(upright).sub(0, 12, 0)).add(hips);
    }
    /** Removal is not dressing; replacement in an occupied slot is. */
    static boolean equipped(List<String> before, List<String> after) {
        for (int i = 0; i < after.size(); i++) {
            String old = i < before.size() ? before.get(i) : "-", next = after.get(i);
            if (!next.equals("-") && !next.equals(old)) return true;
        }
        return false;
    }
}
