package strm.emfcompat.animationadditions.blockuse;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Small shared upper-body correction: both fixed-length arms retain their contacts. */
final class CockpitContact {
    static Quaternionf fit(Vector3f right,Vector3f left,Vector3f rTarget,Vector3f lTarget) {
        Vector3f angles=new Vector3f();float best=score(angles,right,left,rTarget,lTarget);
        if(best<1)return new Quaternionf();
        for(float degrees:new float[]{12,6,3,1.5f,.75f})for(int axis=0;axis<3;axis++) {
            Vector3f chosen=new Vector3f(angles);
            for(int sign:new int[]{-1,1}) {
                Vector3f candidate=new Vector3f(angles);candidate.setComponent(axis,candidate.get(axis)+sign*(float)Math.toRadians(degrees));
                float limit=(float)Math.toRadians(25);if(candidate.length()>limit)candidate.normalize(limit);
                float next=score(candidate,right,left,rTarget,lTarget);
                if(next<best){best=next;chosen.set(candidate);}
            }
            angles.set(chosen);
        }
        return new Quaternionf().rotationZYX(angles.z,angles.y,angles.x);
    }
    private static float score(Vector3f a,Vector3f right,Vector3f left,Vector3f rt,Vector3f lt) {
        var q=new Quaternionf().rotationZYX(a.z,a.y,a.x);
        float r=q.transform(new Vector3f(right)).distance(rt)-11,l=q.transform(new Vector3f(left)).distance(lt)-11;
        return r*r+l*l+.15f*a.lengthSquared();
    }
}
