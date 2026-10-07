package strm.touchnmotion.torso;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TwoHandReachMathTest {
    @Test void swappingTheMainHandDoesNotChangeTheBodyPoseAroundAnOrbit() {
        Vector3f r = new Vector3f(-5, -10, 0), l = new Vector3f(5, -10, 0);
        for (int degree = 0; degree < 360; degree++) {
            float angle = (float) Math.toRadians(degree);
            Vector3f a = new Vector3f(-8 * (float) Math.cos(angle), -10 + 8 * (float) Math.sin(angle), -13);
            Vector3f b = new Vector3f(-a.x, -20 - a.y, -13);
            var first = TwoHandReachMath.fit(r, a, l, b, 11, (float) Math.toRadians(40));
            var swapped = TwoHandReachMath.fit(l, b, r, a, 11, (float) Math.toRadians(40));
            assertTrue(first.shift().distance(swapped.shift()) < 1e-4);
            assertTrue(first.turn().transform(new Vector3f(0, -1, 0)).distance(
                    swapped.turn().transform(new Vector3f(0, -1, 0))) < 1e-4);
            assertTrue(first.shift().length() <= 3.5001);
            assertEquals(0, first.shift().y);
            assertTrue(first.turn().angle() <= Math.toRadians(40) + 1e-4);
        }
    }

    @Test void symmetricWheelDoesNotPullThePelvisTowardOnlyOneHand() {
        var fit = TwoHandReachMath.fit(new Vector3f(-5, -10, 0), new Vector3f(-8, -10, -15),
                new Vector3f(5, -10, 0), new Vector3f(8, -10, -15), 11, (float) Math.toRadians(40));
        assertEquals(0, fit.shift().x, 1e-5);
        assertTrue(fit.shift().z < 0);
    }
}
