package strm.emfcompat.animationadditions.interaction;

import strm.emfcompat.core.ik.IKMath;

/** Final-shoulder direction, blended once from the pose before preliminary hand IK. */
public final class ContactAim {
    private ContactAim() {}

    public static float[] rotation(float[] base, float x, float y, float z, float weight) {
        float[] aim = ArmAim.angles(x, y, z);
        if (aim == null) return new float[]{base[0], base[1], base[2]};
        return blend(base,aim,weight);
    }

    /** A brushing hand may trail the canopy, but must not snap when its nearest plant changes. */
    public static float follow(float current,float wanted,double dt,double tau,double radiansPerSecond) {
        if(dt<=0)return current;
        double step=IKMath.wrap(wanted-current)*Smoothing.follow(dt,tau);
        double limit=radiansPerSecond*dt;
        return current+(float)Math.max(-limit,Math.min(limit,step));
    }

    public static float[] blend(float[] base,float[] aim,float weight) {
        weight = Math.max(0, Math.min(1, weight));
        return new float[]{base[0] + IKMath.wrap(aim[0] - base[0]) * weight,
                base[1] + IKMath.wrap(aim[1] - base[1]) * weight, base[2] * (1-weight)};
    }
}
