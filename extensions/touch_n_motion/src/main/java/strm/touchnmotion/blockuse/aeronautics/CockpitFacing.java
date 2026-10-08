package strm.touchnmotion.blockuse.aeronautics;

import strm.touchnmotion.blockuse.*;

/** Angle carrying model forward (-Z) to the projected wheel direction. */
public final class CockpitFacing {
    public static org.joml.Quaternionf orientation(org.joml.Vector3f toward, org.joml.Vector3f up) {
        var y = new org.joml.Vector3f(up).normalize().negate();
        var z = new org.joml.Vector3f(toward).sub(new org.joml.Vector3f(y).mul(toward.dot(y)));
        if (z.lengthSquared() < 1e-6f) return new org.joml.Quaternionf();
        z.normalize().negate();
        var x = new org.joml.Vector3f(y).cross(z).normalize();
        z.set(x).cross(y).normalize();
        return new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f().setColumn(0, x).setColumn(1, y).setColumn(2, z));
    }
    public static float angle(float x, float z) {
        return x * x + z * z < 1e-6f ? 0 : (float) Math.atan2(-x, -z);
    }
    /** {yaw, pitch} of a head, radians, that looks along {@code view} (model space) from a body turned to its seat: as far round as a neck goes. */
    public static float[] look(org.joml.Vector3f view) {
        return new float[]{(float) Math.toRadians(head((float) Math.toDegrees(angle(view.x, view.z)), 0)),
                (float) Math.atan2(view.y, Math.sqrt(view.x * view.x + view.z * view.z))};
    }
    public static float head(float original, float bodyTurn) {
        float degrees = original - (float) Math.toDegrees(bodyTurn);
        degrees = (degrees + 180) % 360;
        if (degrees < 0) degrees += 360;
        return Math.max(-85, Math.min(85, degrees - 180));
    }
}
