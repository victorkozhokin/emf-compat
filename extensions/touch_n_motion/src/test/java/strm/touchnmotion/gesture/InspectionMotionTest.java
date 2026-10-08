package strm.touchnmotion.gesture;

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
        assertEquals(0, InspectionMotion.at(.84f).left());
        assertTrue(InspectionMotion.at(.84f).spread() > .8f);
        assertEquals(0, InspectionMotion.at(0).spread());
        assertEquals(0, InspectionMotion.at(1).spread());
        assertEquals(0, InspectionMotion.at(1).look());
        assertEquals(0, InspectionMotion.at(1).support());
    }

    @Test void twoDistinctAnglesHaveQuietLivingHoldsAndOneSmoothLanding() {
        assertTrue(InspectionMotion.at(.46f).turnLeft() > .14f);
        assertTrue(InspectionMotion.at(.65f).turnLeft() < -.24f);
        for (float start : new float[]{.41f, .60f}) {
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
            for (int i = 0; i <= 100; i++) {
                var p = InspectionMotion.at(start + i * .001f);
                assertTrue(p.left() > .97f, "foot remains lifted throughout both holds");
                min = Math.min(min, p.turnLeft());
                max = Math.max(max, p.turnLeft());
            }
            assertTrue(max - min > .003f, "hold has a small authored adjustment");
            assertTrue(max - min < .025f, "hold is quiet rather than a repeated turn");
        }
        var previous = InspectionMotion.at(0);
        for (int i = 1; i <= 1000; i++) {
            var p = InspectionMotion.at(i / 1000f);
            assertTrue(Math.abs(p.turnLeft() - previous.turnLeft()) < .007f);
            assertTrue(Math.abs(p.left() - previous.left()) < .014f);
            if (i > 700) assertTrue(p.left() <= previous.left() + .00001f);
            previous = p;
        }
        assertEquals(0, previous.left());
        assertEquals(0, previous.turnLeft());
    }
}
