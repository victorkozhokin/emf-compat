package strm.emfcompat.animationadditions.plantreach;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanopyContactTest {
    @Test void contactsStayInsidePlantAtStraightArmLength() {
        var points=CanopyContact.points(0,0,.4,.75,-.4,.4,.3,.8,0,0);
        assertFalse(points.isEmpty());
        for(var point:points) {
            assertTrue(point.x()>=-.4-1e-8 && point.x()<=.4+1e-8);
            assertTrue(point.z()>=.3-1e-8 && point.z()<=.8+1e-8);
            assertEquals(.75,Math.sqrt(point.x()*point.x()+point.z()*point.z()+.4*.4),1e-8);
        }
    }
    @Test void nearbyPlantInsideArmCircleAndLowPlantCannotFakeContact() {
        assertTrue(CanopyContact.points(0,0,.4,.75,-.1,.1,-.1,.1,0,0).isEmpty());
        assertTrue(CanopyContact.points(0,0,.8,.75,-1,1,-1,1,0,0).isEmpty());
    }
}
