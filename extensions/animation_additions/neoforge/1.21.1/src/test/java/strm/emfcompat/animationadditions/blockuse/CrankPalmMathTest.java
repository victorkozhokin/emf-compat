package strm.emfcompat.animationadditions.blockuse;

import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class CrankPalmMathTest {
    @Test void childMeshOffsetFollowsTheEntireCrankOrbit() {
        for (Vector3f palm : new Vector3f[]{new Vector3f(-.5f,9.75f,0),new Vector3f(-1,9.75f,0),new Vector3f(1,10,0)})
            for (int degree=0; degree<=360; degree++) {
                double angle=Math.toRadians(degree);
                Vector3f direction=new Vector3f(2,(float)Math.sin(angle)*7,(float)Math.cos(angle)*7-12);
                Vector3f expected=new Vector3f(direction).normalize().mul(palm.length());
                Vector3f actual=CrankPalmMath.rotation(palm,direction).transform(new Vector3f(palm));
                assertTrue(actual.distance(expected)<1e-4f);
            }
    }
    @Test void aimRemainsFiniteAtVerticalAndSidewaysTargets() {
        Vector3f palm=new Vector3f(-.5f,9.75f,0);
        for (Vector3f direction:new Vector3f[]{new Vector3f(0,-1,0),new Vector3f(0,1,0),new Vector3f(1,0,0),new Vector3f(-1,0,0)}) {
            var q=CrankPalmMath.rotation(palm,direction);
            assertTrue(Float.isFinite(q.x+q.y+q.z+q.w));
            assertTrue(q.transform(new Vector3f(palm)).distance(new Vector3f(direction).mul(palm.length()))<1e-4f);
        }
    }
}
