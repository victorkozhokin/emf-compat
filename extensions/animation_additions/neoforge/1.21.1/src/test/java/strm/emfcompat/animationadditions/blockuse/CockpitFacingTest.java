package strm.emfcompat.animationadditions.blockuse;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import static org.junit.jupiter.api.Assertions.*;
class CockpitFacingTest {
    @Test void facingTracksWheelThroughoutCameraTurn() {
        for (int degrees = -180; degrees <= 180; degrees += 5) {
            float yaw = (float) Math.toRadians(degrees);
            Vector3f wheel = new Quaternionf().rotationY(yaw).transform(new Vector3f(0, 0, -1));
            Vector3f locked = new Quaternionf().rotationY(CockpitFacing.angle(wheel.x, wheel.z)).transform(new Vector3f(0, 0, -1));
            assertTrue(locked.distance(wheel) < 1e-5);
        }
        assertEquals(0, CockpitFacing.angle(0, 0));
    }
    @Test void headCompensatesBodyTurnAndKeepsAnatomicalLimit() {
        assertEquals(0, CockpitFacing.head(60, (float) Math.toRadians(60)), 1e-4);
        assertEquals(85, CockpitFacing.head(150, 0));
        assertEquals(-85, CockpitFacing.head(-150, 0));
        assertEquals(20, CockpitFacing.head(-170, (float) Math.toRadians(170)), 1e-4);
    }
    @Test void deckFrameKeepsForwardAndUpThroughPitchRollAndCameraYaw() {
        for (int yaw = -180; yaw <= 180; yaw += 15) for (int pitch : new int[]{-35, 0, 35}) for (int roll : new int[]{-20, 0, 20}) {
            var expected = new Quaternionf().rotationYXZ((float) Math.toRadians(yaw), (float) Math.toRadians(pitch), (float) Math.toRadians(roll));
            var forward = expected.transform(new Vector3f(0, 0, -1));
            var up = expected.transform(new Vector3f(0, -1, 0));
            var actual = CockpitFacing.orientation(new Vector3f(forward).add(new Vector3f(up).mul(2)), up);
            assertTrue(actual.transform(new Vector3f(0, 0, -1)).distance(forward) < 1e-5);
            assertTrue(actual.transform(new Vector3f(0, -1, 0)).distance(up) < 1e-5);
        }
    }
}
