package strm.emfcompat.animationadditions.blockuse;

import org.junit.jupiter.api.Test;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class CrankTiptoeMathTest {
    @Test void toeRisePreservesTheLowestFootEdgeThroughoutTheBlend() {
        Vector3f hip=new Vector3f(2,12,1);
        for(float pitch:new float[]{-.1f,.1f,.3f})for(int i=0;i<=100;i++) {
            float w=i/100f;
            Quaternionf original=new Quaternionf().rotationZYX(.1f,.2f,pitch);
            Quaternionf toe=new Quaternionf(original).rotateX(.1f*w);
            float before=original.transform(new Vector3f(0,12,0)).add(hip).y+CrankTiptoeMath.span(original);
            Vector3f moved=CrankTiptoeMath.hip(hip,original,toe,12,1+.12f*w);
            float after=toe.transform(new Vector3f(0,12*(1+.12f*w),0)).add(moved).y+CrankTiptoeMath.span(toe);
            assertEquals(before,after,1e-4);
        }
    }
    @Test void torsoExtensionKeepsItsRealHipAttachment() {
        Vector3f hips=new Vector3f(0,11,-2),body=new Vector3f(1,4,-4);
        Quaternionf q=new Quaternionf().rotationZYX(.1f,.2f,.5f);
        Vector3f local=q.conjugate(new Quaternionf()).transform(new Vector3f(hips).sub(body));
        Vector3f moved=CrankTiptoeMath.stretch(body,hips,q,1.1f);
        local.y*=1.1f;
        assertTrue(new Quaternionf(q).transform(local).add(moved).distance(hips)<1e-4f);
        assertEquals(hips,CrankTiptoeMath.stretch(hips,hips,q,1.1f));
    }
}
