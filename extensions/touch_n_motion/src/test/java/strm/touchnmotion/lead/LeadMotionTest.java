package strm.touchnmotion.lead;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LeadMotionTest {
    @Test void smallNetworkSpeedNoiseDoesNotInventRepeatedJerks() {
        LeadMotion m = new LeadMotion();
        m.advance(7, 4, .02, true);
        for (int i = 0; i < 300; i++) {
            m.advance(7, i % 2 == 0 ? 3.8 : 4.2, .02, false);
            assertEquals(0, m.jerk);
        }
    }

    @Test void slackAndElasticDistancesMatchVanillaWithoutHardPoseSwitch() {
        for (double distance : new double[]{2, 4.5, 5.25, 6, 9.5}) {
            LeadMotion m = new LeadMotion();
            for (int i = 0; i < 300; i++) m.advance(distance, 0, .02, i == 0);
            float expected = distance <= 4.5 ? 0 : distance >= 6 ? 1 : .5f;
            assertEquals(expected, m.load, 1e-5);
            assertEquals(0, m.jerk);
        }
    }
    @Test void sustainedSpeedDoesNotRepeatedlyProduceJerksAndClosingDoesNotPull() {
        LeadMotion m = new LeadMotion();
        m.advance(7, 0, .02, true);
        m.advance(7, 4, .02, false);
        assertTrue(m.jerk > .9);
        for (int i = 0; i < 150; i++) m.advance(7, 4, .02, false);
        assertTrue(m.jerk < .001);
        m.advance(7, -4, .02, false);
        assertTrue(m.jerk < .001);
        m.advance(3, 8, .02, false);
        assertTrue(m.jerk < .001, "Slack rope must not transmit impulse");
    }
    @Test void reattachmentOrTeleportDoesNotInventAnImpulse() {
        LeadMotion m = new LeadMotion();
        m.advance(7, 8, .02, true);
        assertEquals(0, m.jerk);
        m.advance(7, 12, .02, true);
        assertEquals(0, m.jerk);
        m.advance(7, 12, 0, false);
        assertEquals(0, m.jerk);
    }
    @Test void tensionFollowsEquallyAtDifferentFrameRatesAndReturnsSmoothly() {
        float[] loads = new float[3];
        int k = 0;
        for (int fps : new int[]{30, 60, 144}) {
            LeadMotion m = new LeadMotion();
            for (int i = 0; i < fps; i++) m.advance(7, 0, 1.0 / fps, i == 0);
            loads[k++] = m.load;
            float previous = m.load;
            for (int i = 0; i < fps; i++) {
                m.advance(3, 0, 1.0 / fps, false);
                assertTrue(m.load <= previous && m.load >= 0);
                previous = m.load;
            }
            assertTrue(m.load < .001);
        }
        assertEquals(loads[0], loads[1], 1e-5);
        assertEquals(loads[0], loads[2], 1e-5);
    }
}
