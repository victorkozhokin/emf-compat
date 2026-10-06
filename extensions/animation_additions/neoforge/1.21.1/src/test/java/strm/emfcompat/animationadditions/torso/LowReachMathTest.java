package strm.emfcompat.animationadditions.torso;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LowReachMathTest {
    @Test void fitsBothCloseAndDistantTargetsWithoutStretchingArm() {
        Vector3f shoulder = new Vector3f(-6, -10, 0);
        for (Vector3f target : new Vector3f[]{new Vector3f(-5, -2, -3), new Vector3f(-8, 2, -8)}) {
            float before = shoulder.distance(target);
            Vector3f fitted = LowReachMath.turn(shoulder, target, 11, (float) Math.PI).transform(new Vector3f(shoulder));
            assertEquals(11, fitted.distance(target), 1e-4);
            assertTrue(Math.abs(before - 11) > 1);
            assertEquals(shoulder.length(), fitted.length(), 1e-4);
        }
    }
    @Test void correctionIsBoundedAndDegenerateTargetsStayFinite() {
        Vector3f shoulder = new Vector3f(0, -10, 0);
        for (Vector3f target : new Vector3f[]{new Vector3f(), new Vector3f(0, -2, 0), new Vector3f(0, 100, 0), new Vector3f(100, 5, 0)}) {
            var q = LowReachMath.turn(shoulder, target, 11, .4f);
            assertTrue(Float.isFinite(q.x + q.y + q.z + q.w));
            assertTrue(2 * Math.acos(Math.min(1, Math.abs(q.w))) <= .4001);
            Vector3f shift = LowReachMath.shift(shoulder, target, 11, 3.5f);
            assertEquals(0, shift.y);
            assertTrue(shift.length() <= 3.5001);
        }
    }
    @Test void horizontalShiftApproachesFarTargetAndRetreatsFromCloseOne() {
        Vector3f shoulder = new Vector3f();
        assertEquals(3.5f, LowReachMath.shift(shoulder, new Vector3f(20, 0, 0), 11, 3.5f).x);
        assertEquals(-3.5f, LowReachMath.shift(shoulder, new Vector3f(2, 0, 0), 11, 3.5f).x);
    }
}
