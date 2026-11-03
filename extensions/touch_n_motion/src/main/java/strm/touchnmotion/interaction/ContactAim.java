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

    public static float[] blend(float[] base, float[] aim, float weight) {
        weight = Ease.unit(weight);
        return new float[]{IKMath.approachAngle(base[0], aim[0], weight),
                IKMath.approachAngle(base[1], aim[1], weight), base[2] * (1 - weight)};
    }
}
