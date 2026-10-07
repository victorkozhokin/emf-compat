package strm.emfcompat.animationadditions.transport;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import strm.emfcompat.animationadditions.interaction.Ease;
class BraceMathTest {
    @Test void retainedReachHasAHysteresisButCannotCrossTheOppositeHand() {
        Vector3f shoulder = new Vector3f(-5, 2, 0), point = new Vector3f(-5, 2, -13.5f);
        assertFalse(BraceMath.reachable(point, shoulder, true, false));
        assertTrue(BraceMath.reachable(point, shoulder, true, true));
        assertFalse(BraceMath.reachable(new Vector3f(-5, 2, -15), shoulder, true, true));
        assertFalse(BraceMath.reachable(new Vector3f(3, 2, -4), shoulder, true, true));
        assertFalse(BraceMath.reachable(new Vector3f(-5, 2, -2), shoulder, true, true));
    }
    @Test void feetWidenAndStaggerInResponseToLoadRatherThanSharingOnePoint() {
        for (Vector3f force : new Vector3f[]{new Vector3f(0, 0, 1), new Vector3f(0, 0, -1), new Vector3f(1, 0, 0)}) {
            Vector3f r = BraceMath.foot(true, force, 1), l = BraceMath.foot(false, force, 1);
            assertTrue(l.x - r.x > 2.5);
            assertEquals(0, r.y);
            assertEquals(0, l.y);
            assertTrue(r.length() < 2);
            assertTrue(l.length() < 2);
        }
        assertEquals(-BraceMath.foot(true, new Vector3f(0, 0, 1), 1).z,
                BraceMath.foot(true, new Vector3f(0, 0, -1), 1).z);
    }
    @Test void effortAndStepInterpolationAreBounded() {
        assertEquals(0, BraceMath.load(-4));
        assertEquals(1, BraceMath.load(100));
        assertEquals(0, Ease.smooth(-1));
        assertEquals(1, Ease.smooth(2));
        assertEquals(.5, Ease.smooth(.5f));
    }
}
