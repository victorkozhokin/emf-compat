package strm.touchnmotion.torso;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClearancePoseTest {
    @Test void deepSquatGetsBoundedReliefOnlyAtAnObstacle() {
        assertEquals(0, ClearancePose.lift(0, 1));
        assertEquals(0, ClearancePose.lift(1, 0));
        assertEquals(3.25f, ClearancePose.lift(1, 1));
        assertEquals(3.25f, ClearancePose.lift(1, 1));
    }
    @Test void entryAndReleaseAreContinuousWithTheClearanceBlend() {
        float before = 0;
        for (int i = 0; i <= 1000; i++) {
            float now = ClearancePose.lift(i / 1000f, 1);
            assertTrue(now - before <= .00326f);
            before = now;
        }
        assertEquals(1.625f, ClearancePose.lift(1, .5f));
    }
}
