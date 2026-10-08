package strm.touchnmotion.buttonpress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReachPoseTest {

    @Test
    void thePoseIsOnlyBetweenTheArmsLengthAndTooFar() {
        assertEquals(0f, ReachPose.weight(0.5f));
        assertEquals(0f, ReachPose.weight(0.95f));
        assertEquals(1f, ReachPose.weight(1.3f));
        assertEquals(1f, ReachPose.weight(1.8f));
        assertEquals(0f, ReachPose.weight(2.4f));
        assertEquals(0f, ReachPose.weight(4f));
    }

    @Test
    void itComesAndGoesWithoutAStep() {
        float last = ReachPose.weight(0.5f);
        for (float reach = 0.5f; reach < 3f; reach += 0.005f) {
            float w = ReachPose.weight(reach);
            assertTrue(w >= 0f && w <= 1f);
            assertTrue(Math.abs(w - last) < 0.03f, "at " + reach);
            last = w;
        }
    }

    @Test
    void theLeanGoesTowardsTheTargetAndDeeperForOneBelowTheWaist() {
        float[] ahead = new float[3], low = new float[3], right = new float[3];
        ReachPose.lean(new org.joml.Vector3f(0, 2, -15), 1f, ahead);
        ReachPose.lean(new org.joml.Vector3f(0, 24, -15), 1f, low);
        ReachPose.lean(new org.joml.Vector3f(-15, 2, 0), 1f, right);
        assertEquals(Math.toRadians(25), ahead[0], 1e-4);
        assertEquals(Math.toRadians(50), low[0], 1e-4);
        assertEquals(0f, ahead[2], 1e-6f);
        // To the right (-x) is a roll to the right, +zRot.
        assertEquals(Math.toRadians(25), right[2], 1e-4);
        assertEquals(0f, right[0], 1e-6f);
    }
}
