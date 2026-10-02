package strm.emfcompat.animationadditions.blockuse;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import static org.junit.jupiter.api.Assertions.*;
class CockpitFacingTest {
    @Test void facingTracksWheelThroughoutCameraTurn() {
        for(int degrees=-180;degrees<=180;degrees+=5) {
            float yaw=(float)Math.toRadians(degrees);
            Vector3f wheel=new Quaternionf().rotationY(yaw).transform(new Vector3f(0,0,-1));
            Vector3f locked=new Quaternionf().rotationY(CockpitFacing.angle(wheel.x,wheel.z)).transform(new Vector3f(0,0,-1));
            assertTrue(locked.distance(wheel)<1e-5);
        }
        assertEquals(0,CockpitFacing.angle(0,0));
    }
    @Test void headCompensatesBodyTurnAndKeepsAnatomicalLimit() {
        assertEquals(0,CockpitFacing.head(60,(float)Math.toRadians(60)),1e-4);
        assertEquals(85,CockpitFacing.head(150,0));
        assertEquals(-85,CockpitFacing.head(-150,0));
        assertEquals(20,CockpitFacing.head(-170,(float)Math.toRadians(170)),1e-4);
    }
}
