package strm.touchnmotion.transport;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class TransportMotionTest {
    @Test void constantVelocityDoesNotInventSustainedAcceleration() {
        TransportMotion m = new TransportMotion();
        for (int i = 0; i < 100; i++) m.sample(i, new Vector3d(i * .1, 0, 0));
        assertEquals(2, m.speed, 1e-8);
        assertTrue(m.acceleration.length() < 1e-5);
        Vector3d unchanged = new Vector3d(m.acceleration);
        m.sample(99, new Vector3d(100, 0, 0));
        assertEquals(unchanged, m.acceleration);
    }
    @Test void accelerationAndBrakingHaveOppositeLoadDirections() {
        TransportMotion m = new TransportMotion();
        for (int i = 0; i < 20; i++) m.sample(i, new Vector3d(i * i * .002, 0, 0));
        assertTrue(m.acceleration.x > 1);
        double x = 19 * 19 * .002;
        for (int i = 20; i < 50; i++) m.sample(i, new Vector3d(x, 0, 0));
        assertTrue(m.acceleration.x < 0);
        assertTrue(m.acceleration.length() <= 12.001);
    }
    @Test void rotationOfADeckPointHasCentripetalLoadWithoutCameraInput() {
        TransportMotion m = new TransportMotion();
        for (int i = 0; i < 100; i++) { double a = i * .025; m.sample(i, new Vector3d(4 * Math.cos(a), 0, 4 * Math.sin(a))); }
        assertTrue(m.speed > 1.9);
        assertTrue(m.acceleration.length() > .7);
    }
    @Test void TeleportsAndSampleGapsResetRatherThanThrowingTheBody() {
        TransportMotion m = new TransportMotion();
        m.sample(1, new Vector3d());
        m.sample(2, new Vector3d(5, 0, 0));
        assertTrue(m.warped);
        assertEquals(0, m.speed);
        assertEquals(0, m.acceleration.length());
        m.sample(3, new Vector3d(5, 0, 0));
        assertFalse(m.warped);
        m.sample(20, new Vector3d(5.5, 0, 0));
        assertEquals(0, m.acceleration.length());
    }
}
