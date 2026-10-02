package strm.emfcompat.animationadditions.blockuse;
import org.joml.Quaternionf;
import org.joml.Vector3f;
final class CrankTiptoeMath {
    static float span(Quaternionf q) {
        return 2*(Math.abs(q.transform(new Vector3f(1,0,0)).y)+Math.abs(q.transform(new Vector3f(0,0,1)).y));
    }
    static Vector3f hip(Vector3f pivot,Quaternionf before,Quaternionf after,float length,float ratio) {
        Vector3f sole=before.transform(new Vector3f(0,length,0)).add(pivot);
        sole.y+=span(before)-span(after);
        return sole.sub(after.transform(new Vector3f(0,length*ratio,0)));
    }
    static Vector3f stretch(Vector3f point,Vector3f hips,Quaternionf body,float ratio) {
        Vector3f local=new Quaternionf(body).conjugate().transform(new Vector3f(point).sub(hips));local.y*=ratio;
        return new Quaternionf(body).transform(local).add(hips);
    }
}
