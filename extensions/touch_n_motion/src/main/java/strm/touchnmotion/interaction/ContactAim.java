package strm.touchnmotion.interaction;

import strm.emfcompat.core.ik.IKMath;

/** Final-shoulder direction, blended once from the pose before preliminary hand IK. */
public final class ContactAim {
    private ContactAim() {}

    public static float[] rotation(float[] base, float x, float y, float z, float weight) {
        IKMath.Angles aim = IKMath.anglesXY(x, y, z);
        if (aim == null) return new float[]{base[0], base[1], base[2]};
        return blend(base, new float[]{aim.x(), aim.secondary()}, weight);
    }

    /** Palm contact keeps the existing roll while turning its rigid axis towards the point. */
    public static float[] palmRotation(float[] base, float x, float y, float z, float weight) {
        var direction = new org.joml.Vector3f(x, y, z);
        if (!direction.isFinite() || direction.lengthSquared() < 1e-5f)
            return new float[]{base[0], base[1], base[2]};
        var wanted = new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0), direction.normalize());
        var angles = IKMath.eulerZYX(new org.joml.Quaternionf().rotationZYX(base[2], base[1], base[0])
                .slerp(wanted, Ease.unit(weight)));
        return new float[]{angles.x, angles.y, angles.z};
    }

    public static float[] blend(float[] base, float[] aim, float weight) {
        weight = Ease.unit(weight);
        return new float[]{IKMath.approachAngle(base[0], aim[0], weight),
                IKMath.approachAngle(base[1], aim[1], weight), base[2] * (1 - weight)};
    }
}
