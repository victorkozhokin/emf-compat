package strm.emfcompat.animationadditions.blockuse;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Orient the drawn palm rather than a nominal arm endpoint. */
final class CrankPalmMath {
    static Quaternionf rotation(Vector3f palm, Vector3f direction) {
        Vector3f to = new Vector3f(direction).normalize();
        float pitch = -(float) Math.acos(Math.max(-1, Math.min(1, to.y)));
        float yaw = (float) Math.atan2(-to.x, -to.z);
        return new Quaternionf().rotationZYX(0, yaw, pitch)
                .mul(new Quaternionf().rotationTo(palm, new Vector3f(0, palm.length(), 0))).normalize();
    }
}
