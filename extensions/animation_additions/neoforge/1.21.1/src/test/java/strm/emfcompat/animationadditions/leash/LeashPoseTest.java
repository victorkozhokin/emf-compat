package strm.emfcompat.animationadditions.leash;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import static org.junit.jupiter.api.Assertions.*;
class LeashPoseTest {
    @Test void backwardGripDoesNotFlipYawAcrossDownwardPole() {
        Vector3f last = null;
        for (int i = 0; i <= 240; i++) {
            Vector3f grip = new Vector3f(-.8f, 8, -3 + i * .025f);
            Vector3f a = LeashPose.angles(LeashPose.swing(grip));
            assertTrue(Math.abs(a.y) < .2f);
            if (last != null) assertTrue(a.distance(last) < .02f);
            Vector3f reached = new Quaternionf().rotationZYX(a.z, a.y, a.x).transform(new Vector3f(0, 1, 0));
            assertEquals(0, reached.distance(new Vector3f(grip).normalize()), 1e-5);
            last = a;
        }
    }
    @Test void bothHandsStayOnOwnSideAndBehindPullActuallyGoesBack() {
        for (boolean right : new boolean[]{true, false}) for (float z : new float[]{-1, 0, 1}) {
            Vector3f g = LeashPose.grip(new Vector3f(0, -1, z).normalize(), right, 1, 1);
            assertTrue(g.y >= 3);
            assertTrue(right ? g.x < 0 : g.x > 0);
            if (z > 0) assertTrue(g.z > 0);
        }
    }
    @Test void StanceMirrorsAndRemainsSmall() {
        Vector3f d = new Vector3f(0, 0, 1);
        Vector3f r = LeashPose.foot(true, d, 1), l = LeashPose.foot(false, d, 1);
        assertEquals(0, new Vector3f(r).add(l).length(), 1e-6);
        assertTrue(r.length() < 2);
        assertEquals(0, LeashPose.foot(true, d, 0).length());
    }
    @Test void stopIsOneShortPulseAndDoesNotRetriggerAtRest() {
        LeashStopGesture s = new LeashStopGesture();
        for (int i = 0; i < 30; i++) assertEquals(0, s.advance(3, 4, .02, false), 1e-6);
        float max = 0;
        for (int i = 0; i < 35; i++) max = Math.max(max, s.advance(0, 4, .02, false));
        assertTrue(max > .99);
        for (int i = 0; i < 100; i++) assertEquals(0, s.advance(0, 4, .02, false), 1e-6);
    }
    @Test void noiseSlackAndReattachmentCannotTriggerStopPull() {
        LeashStopGesture s = new LeashStopGesture();
        for (int i = 0; i < 100; i++) assertEquals(0, s.advance(i % 2 == 0 ? .2 : .4, 4, .02, false), 1e-6);
        for (int i = 0; i < 30; i++) s.advance(3, 1, .02, false);
        for (int i = 0; i < 40; i++) assertEquals(0, s.advance(0, 1, .02, false), 1e-6);
        for (int i = 0; i < 30; i++) s.advance(3, 4, .02, false);
        s.advance(0, 4, .02, true);
        for (int i = 0; i < 40; i++) assertEquals(0, s.advance(0, 4, .02, false), 1e-6);
    }
}
