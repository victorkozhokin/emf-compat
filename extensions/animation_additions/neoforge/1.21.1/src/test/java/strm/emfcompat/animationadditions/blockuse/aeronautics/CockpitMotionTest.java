package strm.emfcompat.animationadditions.blockuse.aeronautics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import strm.emfcompat.animationadditions.blockuse.*;
class CockpitMotionTest {
    @Test void alternatingControlsAlwaysRetainOneRimHand() {
        CockpitMotion motion = new CockpitMotion();
        for (int request : new int[]{0, 1, 0, -1, 1, -1}) {
            for (int i = 0; i < 80; i++) {
                motion.advance(request, .01f);
                assertTrue(motion.mix(0) == 0 || motion.mix(1) == 0);
                assertTrue(motion.mix(0) >= 0 && motion.mix(0) <= 1);
                assertTrue(motion.mix(1) >= 0 && motion.mix(1) <= 1);
            }
            assertEquals(request, motion.away);
        }
    }
    @Test void rapidReversalCompletesReturnBeforeReleasingOtherHand() {
        CockpitMotion motion = new CockpitMotion();
        motion.advance(0, .08f);
        for (int i = 0; i < 100; i++) {
            motion.advance(1, .01f);
            assertTrue(motion.mix(0) == 0 || motion.mix(1) == 0);
            assertTrue(motion.lift(0) == 0 || motion.lift(1) == 0);
        }
        assertEquals(1, motion.away);
    }
}
