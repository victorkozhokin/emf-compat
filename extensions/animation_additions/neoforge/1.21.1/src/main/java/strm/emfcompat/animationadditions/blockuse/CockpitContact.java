package strm.emfcompat.animationadditions.blockuse;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.joml.Matrix3f;

/** Continuous upper-body reach correction for two fixed-length FA arms. */
final class CockpitContact {
    static Quaternionf fit(Vector3f right,Vector3f left,Vector3f rt,Vector3f lt) {
        return fit(new Quaternionf(),right,left,rt,lt);
    }
    static Quaternionf fit(Quaternionf previous,Vector3f right,Vector3f left,Vector3f rt,Vector3f lt) {
        return fit(previous,right,left,rt,lt,false);
    }
    static Quaternionf fit(Quaternionf previous,Vector3f right,Vector3f left,Vector3f rt,Vector3f lt,boolean sideControl) {
        Vector3f prior=new Quaternionf(previous).getEulerAnglesZYX(new Vector3f());
        Vector3f a=new Vector3f(prior);
        // Two contact distances leave an unconstrained rotational direction. A discrete search
        // could jump between equivalent bends at every regrip. Regularize toward the previous
        // solution and rest posture, using a damped local least-squares update instead.
        final float rest=sideControl?.15f:.6f,continuity=sideControl?.4f:4f,epsilon=.001f,limit=(float)Math.toRadians(30);
        for(int pass=0;pass<18;pass++) {
            float[] error=errors(a,right,left,rt,lt);
            Vector3f[] gradients={new Vector3f(),new Vector3f()};
            for(int axis=0;axis<3;axis++) {
                Vector3f shifted=new Vector3f(a).setComponent(axis,a.get(axis)+epsilon);
                float[] changed=errors(shifted,right,left,rt,lt);
                for(int hand=0;hand<2;hand++)gradients[hand].setComponent(axis,(changed[hand]-error[hand])/epsilon);
            }
            Matrix3f h=new Matrix3f().zero();
            Vector3f rhs=new Vector3f(a).mul(-rest).sub(new Vector3f(a).sub(prior).mul(continuity));
            for(int axis=0;axis<3;axis++)h.set(axis,axis,rest+continuity);
            for(int hand=0;hand<2;hand++) {
                rhs.sub(new Vector3f(gradients[hand]).mul(error[hand]));
                for(int col=0;col<3;col++)for(int row=0;row<3;row++)
                    h.set(col,row,h.get(col,row)+gradients[hand].get(col)*gradients[hand].get(row));
            }
            Vector3f step=h.invert().transform(rhs);
            if(!step.isFinite() || step.lengthSquared()<1e-10f)break;
            if(step.length()>.12f)step.normalize(.12f);
            float before=cost(a,prior,error,rest,continuity);boolean improved=false;
            for(float scale=1;scale>=.0625f;scale*=.5f) {
                Vector3f candidate=new Vector3f(a).add(new Vector3f(step).mul(scale));
                if(candidate.length()>limit)candidate.normalize(limit);
                if(cost(candidate,prior,errors(candidate,right,left,rt,lt),rest,continuity)<before) {
                    a.set(candidate);improved=true;break;
                }
            }
            if(!improved)break;
        }
        // A low side keyboard can require another nonlinear reach branch. Keep this recovery
        // out of wheel-only steering; select the nearest useful side pose and still damp its draw.
        if(sideControl && gap(new Quaternionf().rotationZYX(a.z,a.y,a.x),right,left,rt,lt)>2f) {
            Vector3f best=new Vector3f(a);float bestCost=cost(a,prior,errors(a,right,left,rt,lt),rest,continuity);
            for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}) {
                Vector3f trial=new Vector3f().setComponent(axis,sign*(float)Math.toRadians(18));
                trial=sideRefine(trial,prior,right,left,rt,lt,rest,continuity);
                float value=cost(trial,prior,errors(trial,right,left,rt,lt),rest,continuity);
                if(value<bestCost){bestCost=value;best.set(trial);}
            }
            a.set(best);
        }
        return new Quaternionf().rotationZYX(a.z,a.y,a.x);
    }
    private static Vector3f sideRefine(Vector3f a,Vector3f prior,Vector3f r,Vector3f l,Vector3f rt,Vector3f lt,float rest,float continuity) {
        float best=cost(a,prior,errors(a,r,l,rt,lt),rest,continuity);
        for(float degree:new float[]{12,6,3,1.5f,.75f})for(int pass=0;pass<3;pass++)for(int axis=0;axis<3;axis++) {
            Vector3f chosen=new Vector3f(a);
            for(int sign:new int[]{-1,1}) {
                Vector3f trial=new Vector3f(a).setComponent(axis,a.get(axis)+sign*(float)Math.toRadians(degree));
                float limit=(float)Math.toRadians(30);if(trial.length()>limit)trial.normalize(limit);
                float value=cost(trial,prior,errors(trial,r,l,rt,lt),rest,continuity);
                if(value<best){best=value;chosen.set(trial);}
            }
            a.set(chosen);
        }
        return a;
    }
    private static float[] errors(Vector3f a,Vector3f right,Vector3f left,Vector3f rt,Vector3f lt) {
        Quaternionf q=new Quaternionf().rotationZYX(a.z,a.y,a.x);
        return new float[]{q.transform(new Vector3f(right)).distance(rt)-11,
                q.transform(new Vector3f(left)).distance(lt)-11};
    }
    private static float cost(Vector3f a,Vector3f prior,float[] e,float rest,float continuity) {
        return e[0]*e[0]+e[1]*e[1]+rest*a.lengthSquared()+continuity*a.distanceSquared(prior);
    }
    static float gap(Quaternionf q,Vector3f right,Vector3f left,Vector3f rt,Vector3f lt) {
        return Math.max(Math.abs(q.transform(new Vector3f(right)).distance(rt)-11),
                Math.abs(q.transform(new Vector3f(left)).distance(lt)-11));
    }
    static Quaternionf follow(Quaternionf current,Quaternionf wanted,float blend,
            Vector3f right,Vector3f left,Vector3f rt,Vector3f lt) {
        return new Quaternionf(current).slerp(wanted,blend);
    }
}
