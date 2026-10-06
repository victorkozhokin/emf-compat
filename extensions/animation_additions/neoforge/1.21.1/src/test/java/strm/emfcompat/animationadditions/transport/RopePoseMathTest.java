package strm.emfcompat.animationadditions.transport;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RopePoseMathTest {
    @Test void raisedFootRequiresAnEdgeAndAPlantedOppositeSole() {
        assertTrue(RopePoseMath.edgeLift(true, true, true, false, false) > 0);
        assertEquals(0, RopePoseMath.edgeLift(true, true, false, false, false));
        assertEquals(0, RopePoseMath.edgeLift(true, false, true, false, false));
        assertEquals(0, RopePoseMath.edgeLift(false, true, true, false, false));
        assertEquals(0, RopePoseMath.edgeLift(true, true, true, false, true));
    }
    @Test void ropeStanceIsMirroredAndDoesNotDependOnCircularAcceleration() {
        var right = RopePoseMath.foot(true);
        var left = RopePoseMath.foot(false);
        assertEquals(-right.x, left.x);
        assertEquals(right.z, left.z);
        assertTrue(left.z > 0);
        assertEquals(0, right.y);
        assertEquals(0, left.y);
        assertTrue(right.distance(left) > 1);
    }
    @Test void segmentProjectionStaysOnTheRopeAndHandlesDegeneratePoints() {
        Vector3d a = new Vector3d(20_000_000, 1, 0), b = new Vector3d(20_000_000, 5, 0);
        assertEquals(.5, RopePoseMath.fraction(a, b, new Vector3d(20_000_001, 3, 0)), 1e-9);
        assertEquals(0, RopePoseMath.fraction(a, b, new Vector3d(20_000_000, -5, 0)));
        assertEquals(1, RopePoseMath.fraction(a, b, new Vector3d(20_000_000, 10, 0)));
        assertEquals(0, RopePoseMath.fraction(a, a, b));
    }
    @Test void SpeedStrengthensReactionButDoesNotGrowWithoutBound() {
        assertEquals(1, RopePoseMath.gain(0));
        assertTrue(RopePoseMath.gain(4) > RopePoseMath.gain(1));
        assertEquals(1.65, RopePoseMath.gain(100), 1e-6);
        assertEquals(1, RopePoseMath.gain(-1));
    }
    @Test void CrouchingAndTwoHandBracingLowerTheRaisedFoot() {
        assertTrue(RopePoseMath.lift(false, false) > RopePoseMath.lift(true, false));
        assertTrue(RopePoseMath.lift(false, true) < RopePoseMath.lift(false, false));
        assertTrue(RopePoseMath.lift(false, false) < 2);
    }
    @Test void regripCrossesSegmentsContinuouslyAtLargeCoordinates() {
        var points = java.util.List.of(new Vector3d(20_000_000, 1, 0), new Vector3d(20_000_000, 2, 0), new Vector3d(20_000_001, 3, 0));
        var before = RopePoseMath.sample(3, .999, i -> new Vector3d(points.get(i)));
        var after = RopePoseMath.sample(3, 1.001, i -> new Vector3d(points.get(i)));
        assertTrue(before.distance(after) < .003);
        assertEquals(points.getFirst(), RopePoseMath.sample(3, -10, i -> new Vector3d(points.get(i))));
        assertEquals(points.getLast(), RopePoseMath.sample(3, 10, i -> new Vector3d(points.get(i))));
    }
    @Test void regripDoesNotJumpAndIsIndependentOfFrameRate() {
        assertEquals(2.9, RopePoseMath.slide(2.9, 3.2, 0));
        double half = RopePoseMath.slide(2.9, 3.2, .18);
        assertTrue(half > 2.9 && half < 3.2);
        double steps = 2.9;
        for (int i = 0; i < 10; i++) steps = RopePoseMath.slide(steps, 3.2, .05);
        assertEquals(RopePoseMath.slide(2.9, 3.2, .5), steps, 1e-6);
    }
    @Test void secondHandCanBraceASideCordButReachRemainsBounded() {
        var shoulder = new org.joml.Vector3f(5, 2, 0);
        var cord = new org.joml.Vector3f(-4, 4, -9);
        assertFalse(BraceMath.reachable(cord, shoulder, false, true));
        assertTrue(RopePoseMath.reachable(cord, shoulder, false));
        assertFalse(RopePoseMath.reachable(new org.joml.Vector3f(-6.1f, 2, -1), shoulder, false));
        assertFalse(RopePoseMath.reachable(new org.joml.Vector3f(0, 2, -15), shoulder, false));
        assertFalse(RopePoseMath.reachable(new org.joml.Vector3f(5, 2, 1), shoulder, false));
    }
}
