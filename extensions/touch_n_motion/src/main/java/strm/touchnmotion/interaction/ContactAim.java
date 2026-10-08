package strm.touchnmotion.interaction;

import strm.emfcompat.core.ik.IKMath;

/** Final-shoulder direction, blended once from the pose before preliminary hand IK. */
public final class ContactAim {
    private ContactAim() {}

    public static float[] rotation(float[] base, float x, float y, float z, float weight) {
        float[] aim = ArmAim.angles(x, y, z);
        if (aim == null) return new float[]{base[0], base[1], base[2]};
        return blend(base, aim, weight);
    }

    public static float[] blend(float[] base, float[] aim, float weight) {
        weight = Ease.unit(weight);
        return new float[]{base[0] + IKMath.wrap(aim[0] - base[0]) * weight,
                base[1] + IKMath.wrap(aim[1] - base[1]) * weight, base[2] * (1 - weight)};
    }
}
