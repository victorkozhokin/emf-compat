package strm.emfcompat.animationadditions.leash;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shoulder-relative grip and a pole-free swing: the wrist never aims through the chest. */
final class LeashPose {
    static Vector3f grip(Vector3f direction, boolean right, float effort, float jerk) {
        float side=right ? -1 : 1;
        Vector3f rest=new Vector3f(side*.8f,8,-2);
        Vector3f pull=new Vector3f(direction).mul(8);
        pull.x=side*Math.max(.8f,Math.min(5,side*pull.x));
        pull.y=8-Math.max(0,-direction.z)*2-jerk*.5f;
        pull.z=Math.max(-6,Math.min(5,pull.z));
        return rest.lerp(pull,Math.min(1,effort+jerk*.25f));
    }
    static Vector3f angles(Quaternionf q) {
        return new Vector3f(
            (float)Math.atan2(2*(q.w*q.x+q.y*q.z),1-2*(q.x*q.x+q.y*q.y)),
            (float)Math.asin(Math.max(-1,Math.min(1,2*(q.w*q.y-q.z*q.x)))),
            (float)Math.atan2(2*(q.w*q.z+q.x*q.y),1-2*(q.y*q.y+q.z*q.z)));
    }
    static Quaternionf swing(Vector3f grip) {
        return new Quaternionf().rotationTo(new Vector3f(0,1,0),new Vector3f(grip).normalize());
    }
    static Vector3f foot(boolean right,Vector3f direction,float load) {
        // Split the stance along the pull, keeping one supporting foot throughout the setup.
        float side=right ? -1 : 1;
        return new Vector3f(side*.8f*load,0,side*direction.z*1.8f*load);
    }
}
