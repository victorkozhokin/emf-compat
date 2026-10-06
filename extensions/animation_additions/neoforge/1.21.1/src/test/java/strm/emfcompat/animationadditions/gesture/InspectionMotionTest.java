package strm.emfcompat.animationadditions.gesture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InspectionMotionTest {
    @Test void eyesAndSupportLeadTheOnlyLiftAndArmsReturnAfterTheFoot() {
        boolean liftSeen = false;
        for (int i = 0; i <= 1000; i++) {
            var p = InspectionMotion.at(i / 1000f);
            assertTrue(p.left() >= 0 && p.left() <= 1);
            assertTrue(Math.abs(p.turnLeft()) <= .25f);
            if (p.left() > 0) {
                assertTrue(p.look() > .8f, "the gaze reaches the boot before it lifts");
                assertTrue(p.support() > .9f, "weight is on the planted right side before lifting");
                liftSeen = true;
            }
        }
        assertTrue(liftSeen);
        assertEquals(0, InspectionMotion.at(.76f).left());
        assertTrue(InspectionMotion.at(.76f).spread() > .8f);
        assertEquals(0, InspectionMotion.at(0).spread());
        assertEquals(0, InspectionMotion.at(1).spread());
        assertEquals(0, InspectionMotion.at(1).look());
        assertEquals(0, InspectionMotion.at(1).support());
    }
}
