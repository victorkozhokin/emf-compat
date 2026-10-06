package strm.emfcompat.animationadditions.interaction;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlaneContactTest {
    @Test void finalPalmPreservesPlaneAndBoneLengthAsShoulderMoves() {
        for(float x=-2;x<=2;x+=.1f) {
            var shoulder=new Vector3f(x,2,0);
            var palm=PlaneContact.fit(shoulder,new Vector3f(1,8,-6),new Vector3f(0,0,1),10);
            assertNotNull(palm);assertEquals(-6,palm.z,1e-5);assertEquals(10,palm.distance(shoulder),1e-5);
        }
    }
    @Test void unreachablePlaneReturnsNoContactAndDegenerateTangentRemainsFinite() {
        assertNull(PlaneContact.fit(new Vector3f(),new Vector3f(0,0,12),new Vector3f(0,0,1),10));
        var p=PlaneContact.fit(new Vector3f(),new Vector3f(0,0,6),new Vector3f(0,0,1),10);
        assertNotNull(p);assertEquals(10,p.length(),1e-5);
    }
}
