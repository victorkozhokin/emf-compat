package strm.emfcompat.animationadditions.buttonpress;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import strm.emfcompat.animationadditions.interaction.Ease;

public class LeverEffortTest {
    @Test void lowAndHighGripsKeepTheSameSmallGroundedLoad() {
        for (float y : new float[]{-30, 2, 20}) {
            Vector3f load = LeverEffort.shift(new Vector3f(3, y, -12), true);
            assertEquals(.55f, load.length(), 1e-6);
            assertEquals(0, load.y);
            assertTrue(load.x > 0 && load.z < 0);
        }
    }
    @Test void sideReachMirrorsAndHoverIsSubtle() {
        Vector3f right = LeverEffort.shift(new Vector3f(-8, 0, -5), true);
        Vector3f left = LeverEffort.shift(new Vector3f(8, 0, -5), true);
        assertEquals(-right.x, left.x, 1e-6);
        assertEquals(right.z, left.z, 1e-6);
        assertEquals(.12f, LeverEffort.shift(new Vector3f(8, 0, -5), false).length(), 1e-6);
        assertEquals(new Vector3f(), LeverEffort.shift(new Vector3f(0, 20, 0), true));
    }
    @Test void stepHasSmallLandingAndSmoothGroundedEndpoints() {
        assertEquals(.9f, LeverEffort.step(new Vector3f(4, 25, -10)).length(), 1e-6f);
        assertEquals(0, Ease.smooth(0));
        assertEquals(1, Ease.smooth(1));
        assertEquals(0, LeverEffort.lift(0), 1e-6f);
        assertEquals(0, LeverEffort.lift(1), 1e-6f);
        assertEquals(1, LeverEffort.lift(.5f), 1e-6f);
    }
}
