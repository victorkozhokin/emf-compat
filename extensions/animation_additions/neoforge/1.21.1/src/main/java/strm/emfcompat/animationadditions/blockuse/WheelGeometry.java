package strm.emfcompat.animationadditions.blockuse;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/** Renderer-space wheel transforms. Inputs and results are in block units, around block centre. */
public final class WheelGeometry {
    private WheelGeometry() {}

    /** Model Y points down; negative roll carries the upper torso toward the right (-X). */
    public static float steeringRoll(float rightY, float leftY) {
        float difference = Math.max(-1f, Math.min(1f, (leftY - rightY) / 16f));
        return difference * (float) Math.toRadians(5);
    }

    /** Create ValveHandleVisual: orient +Y to facing, then turn about the positive world axis. */
    public static Vector3f valve(Vector3f model, Vector3f facing, float radians) {
        Vector3f p = new Vector3f(model).sub(.5f, .5f, .5f);
        new Quaternionf().rotationTo(new Vector3f(0, 1, 0), facing).transform(p);
        new Quaternionf().rotationAxis(radians, Math.abs(facing.x), Math.abs(facing.y), Math.abs(facing.z)).transform(p);
        return p.add(.5f, .5f, .5f);
    }

    /** Simulated SteeringWheelVisual: local spin, mount offset, then Direction.getRotation(). */
    public static Vector3f steering(Vector3f model, Quaternionfc facing, boolean onFloor, float radians) {
        Vector3f p = new Vector3f(model).sub(.5f, .5f, .5f).rotateY(radians)
                .add(0, 6.5f / 16f, (onFloor ? -5f : 5f) / 16f);
        return new Quaternionf(facing).transform(p).add(.5f, .5f, .5f);
    }
}
