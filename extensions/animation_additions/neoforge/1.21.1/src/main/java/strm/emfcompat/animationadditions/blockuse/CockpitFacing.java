package strm.emfcompat.animationadditions.blockuse;

/** Angle carrying model forward (-Z) to the projected wheel direction. */
final class CockpitFacing {
    static org.joml.Quaternionf orientation(org.joml.Vector3f toward,org.joml.Vector3f up) {
        var y=new org.joml.Vector3f(up).normalize().negate();
        var z=new org.joml.Vector3f(toward).sub(new org.joml.Vector3f(y).mul(toward.dot(y)));
        if(z.lengthSquared()<1e-6f)return new org.joml.Quaternionf();
        z.normalize().negate();var x=new org.joml.Vector3f(y).cross(z).normalize();z.set(x).cross(y).normalize();
        return new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f().setColumn(0,x).setColumn(1,y).setColumn(2,z));
    }
    static float angle(float x,float z) {
        return x*x+z*z<1e-6f ? 0 : (float)Math.atan2(-x,-z);
    }
    static float head(float original,float bodyTurn) {
        float degrees=original-(float)Math.toDegrees(bodyTurn);
        degrees=(degrees+180)%360;
        if(degrees<0) degrees+=360;
        return Math.max(-85,Math.min(85,degrees-180));
    }
}
