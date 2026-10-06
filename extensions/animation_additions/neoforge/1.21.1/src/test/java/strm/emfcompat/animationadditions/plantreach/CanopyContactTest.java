package strm.emfcompat.animationadditions.plantreach;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanopyContactTest {
    @Test void walkingAlongContinuousRowTouchesRimWithoutTrailingOrOrbiting() {
        for (double x = .1; x < 3.9; x += .02) {
            double block = Math.floor(x);
            var p = CanopyContact.edge(x, 1.4, -.3, .65, block, block + 1, 0, 1, 0, 1);
            assertNotNull(p);
            assertEquals(x, p.x(), 1e-8);
            assertEquals(0, p.z(), 1e-8);
            assertEquals(.65, Math.sqrt(Math.pow(p.y() - 1.4, 2) + .3 * .3), 1e-8);
        }
    }
    @Test void distantOrTooLowRimCannotFakeContact() {
        assertNull(CanopyContact.edge(.5, 1.4, -1, .65, 0, 1, 0, 1, 0, 1));
        assertNull(CanopyContact.edge(.5, 1.4, -.3, .65, 0, 1, 0, .3, 0, 1));
    }
    @Test void contactsStayInsidePlantAtStraightArmLength() {
        var points = CanopyContact.points(0, 0, .4, .75, -.4, .4, .3, .8, 0, 0);
        assertFalse(points.isEmpty());
        for (var point : points) {
            assertTrue(point.x() >= -.4 - 1e-8 && point.x() <= .4 + 1e-8);
            assertTrue(point.z() >= .3 - 1e-8 && point.z() <= .8 + 1e-8);
            assertEquals(.75, Math.sqrt(point.x() * point.x() + point.z() * point.z() + .4 * .4), 1e-8);
        }
    }
    @Test void nearbyPlantInsideArmCircleAndLowPlantCannotFakeContact() {
        assertTrue(CanopyContact.points(0, 0, .4, .75, -.1, .1, -.1, .1, 0, 0).isEmpty());
        assertTrue(CanopyContact.points(0, 0, .8, .75, -1, 1, -1, 1, 0, 0).isEmpty());
    }
}
