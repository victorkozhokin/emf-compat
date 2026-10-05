package strm.emfcompat.animationadditions.blockuse;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Small shared upper-body correction: both fixed-length arms retain their contacts. */
final class CockpitContact {
    static Quaternionf fit(Vector3f right,Vector3f left,Vector3f rTarget,Vector3f lTarget) {
        return fit(new Quaternionf(),right,left,rTarget,lTarget);
    }
    static Quaternionf fit(Quaternionf previous,Vector3f right,Vector3f left,Vector3f rTarget,Vector3f lTarget) {
        // Seed from the last solution so adjacent wheel positions keep the same lean branch.
        Vector3f angles=new Quaternionf(previous).getEulerAnglesZYX(new Vector3f());
        float best=score(angles,right,left,rTarget,lTarget);
        if(score(new Vector3f(),right,left,rTarget,lTarget)<1)return new Quaternionf();
        angles=refine(angles,right,left,rTarget,lTarget);
        // Try alternate lean directions only when the local solve still leaves a palm detached.
        // The shallow greedy search can otherwise settle on the wrong side of a low keyboard.
        if(gap(new Quaternionf().rotationZYX(angles.z,angles.y,angles.x),right,left,rTarget,lTarget)>2.08f) {
            best=score(angles,right,left,rTarget,lTarget);
            for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}) {
                var seed=new Vector3f().setComponent(axis,sign*(float)Math.toRadians(18));
                var candidate=refine(seed,right,left,rTarget,lTarget);
                float next=score(candidate,right,left,rTarget,lTarget);
                if(next<best){best=next;angles.set(candidate);}
            }
        }
        return new Quaternionf().rotationZYX(angles.z,angles.y,angles.x);
    }
    private static Vector3f refine(Vector3f angles,Vector3f right,Vector3f left,Vector3f rTarget,Vector3f lTarget) {
        float best=score(angles,right,left,rTarget,lTarget);
        for(float degrees:new float[]{12,6,3,1.5f,.75f})for(int pass=0;pass<3;pass++)for(int axis=0;axis<3;axis++) {
            Vector3f chosen=new Vector3f(angles);
            for(int sign:new int[]{-1,1}) {
                Vector3f candidate=new Vector3f(angles);candidate.setComponent(axis,candidate.get(axis)+sign*(float)Math.toRadians(degrees));
                float limit=(float)Math.toRadians(30);if(candidate.length()>limit)candidate.normalize(limit);
                float next=score(candidate,right,left,rTarget,lTarget);
                if(next<best){best=next;chosen.set(candidate);}
            }
            angles.set(chosen);
        }
        return angles;
    }
    static float gap(Quaternionf q,Vector3f right,Vector3f left,Vector3f rt,Vector3f lt) {
        return Math.max(Math.abs(q.transform(new Vector3f(right)).distance(rt)-11),
                Math.abs(q.transform(new Vector3f(left)).distance(lt)-11));
    }
    static Quaternionf follow(Quaternionf current,Quaternionf wanted,float blend,
            Vector3f right,Vector3f left,Vector3f rt,Vector3f lt) {
        var smooth=new Quaternionf(current).slerp(wanted,blend);
        // Contact error is not evidence of a source-pose discontinuity: it also occurs during
        // normal regrips. Never snap the torso across alternate reach solutions.
        return smooth;
    }
    private static float score(Vector3f a,Vector3f right,Vector3f left,Vector3f rt,Vector3f lt) {
        var q=new Quaternionf().rotationZYX(a.z,a.y,a.x);
        float r=q.transform(new Vector3f(right)).distance(rt)-11,l=q.transform(new Vector3f(left)).distance(lt)-11;
        return r*r+l*l+.15f*a.lengthSquared();
    }
}
