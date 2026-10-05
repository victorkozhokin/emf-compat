package strm.emfcompat.animationadditions.torso;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClearanceOffsetTest {
    @Test void absolutePoseStartsAtItsAuthoredPivotRatherThanTheOrigin() {
        Vector3f pivot=new Vector3f(1,7,4);
        assertEquals(pivot,new ClearanceOffset(true).sample(1,1_000_000_000L,pivot));
    }
    @Test void repeatedModelCopiesCannotAdvanceTheFilterOrMutateItsState() {
        ClearanceOffset filter=new ClearanceOffset();
        filter.sample(0,1_000_000_000L,new Vector3f());
        Vector3f first=filter.sample(1,1_050_000_000L,new Vector3f(0,1,0));
        Vector3f copy=filter.sample(1,1_080_000_000L,new Vector3f(0,-10,0));
        assertEquals(first,copy);copy.y=100;
        assertEquals(first,filter.sample(1,1_090_000_000L,new Vector3f()));
    }
    @Test void strideInducedHipBobIsAttenuatedWithoutChangingTheTargetStance() {
        ClearanceOffset filter=new ClearanceOffset();
        float max=0;
        for(int i=0;i<300;i++) {
            float hip=(float)Math.sin(i/60.0*Math.PI*8)*.75f;
            Vector3f v=filter.sample(i,1_000_000_000L+i*16_666_667L,new Vector3f(0,hip,3.5f));
            if(i>100)max=Math.max(max,Math.abs(v.y));
        }
        assertTrue(max<.25f,"rapid hip motion must not be copied straight to the torso");
        Vector3f v=new Vector3f();
        for(int i=300;i<420;i++)v=filter.sample(i,1_000_000_000L+i*16_666_667L,new Vector3f(0,-3.25f,3.5f));
        assertEquals(-3.25f,v.y,.001f);assertEquals(3.5f,v.z,.001f);
        for(int i=420;i<540;i++)v=filter.sample(i,1_000_000_000L+i*16_666_667L,new Vector3f());
        assertTrue(v.length()<.001f,"clearance must release outside the wall");
    }
}
