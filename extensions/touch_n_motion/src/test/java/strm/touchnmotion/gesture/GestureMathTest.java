package strm.touchnmotion.gesture;

import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GestureMathTest {
    @Test void bodyContactFollowsPelvisAndKeepsItsRadiusDuringBends() {
        Vector3f point = new Vector3f(-3, 5, -3), hips = new Vector3f(1, 13, 2);
        float radius = point.distance(new Vector3f(0, 12, 0));
        for (int i = 0; i <= 100; i++) {
            Vector3f result = GestureMath.bodyPoint(point, hips, i * .007f, i * .004f, i * .001f);
            assertEquals(radius, result.distance(hips), 1e-5f);
            assertTrue(result.isFinite());
        }
        assertEquals(new Vector3f(-2, 6, -1), GestureMath.bodyPoint(point, hips, 0, 0, 0));
    }
    @Test void accessoryReplacementIsDetectedRegardlessOfIdentifierLength() {
        assertTrue(GestureMath.equipped(List.of("long:old_necklace"), List.of("x:a")));
        assertTrue(GestureMath.equipped(List.of("mod:ring_one"), List.of("mod:ring_two")));
        assertTrue(GestureMath.equipped(List.of("mod:ring_one", "-"), List.of("mod:ring_one", "x:b")));
        assertTrue(GestureMath.equipped(List.of(), List.of("x:b")));
        assertFalse(GestureMath.equipped(List.of("mod:ring_one"), List.of("-")));
        assertFalse(GestureMath.equipped(List.of("mod:ring_one", "x:b"), List.of("mod:ring_one")));
        assertFalse(GestureMath.equipped(List.of("mod:ring_one"), List.of("mod:ring_one")));
    }
}
