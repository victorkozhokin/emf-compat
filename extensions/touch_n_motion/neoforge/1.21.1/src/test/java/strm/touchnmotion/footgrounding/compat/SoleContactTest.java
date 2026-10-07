package strm.touchnmotion.footgrounding.compat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SoleContactTest {
    private static float[] pose(float y, float pitch) { return new float[]{pitch, 0, 0, 0, y, 0, 1}; }

    @Test void crouchHipRaiseDoesNotLeaveTwoPixelGapOnFence() {
        assertEquals(2, SoleContact.lowering(pose(10, 0), pose(10, 0)), 1e-6);
        assertEquals(0, SoleContact.lowering(pose(12, 0), pose(12, 0)), 1e-6);
    }

    @Test void plantedFootControlsBodyWhileSwingKeepsItsLift() {
        assertEquals(2, SoleContact.lowering(pose(10, 0), pose(9, 0.5f)), 1e-6);
        assertEquals(2, SoleContact.lowering(pose(9, 0.5f), pose(10, 0)), 1e-6);
    }

    @Test void transitionFollowsPackContinuouslyRatherThanPoseFlag() {
        for (int i = 0; i <= 100; i++) {
            float y = 12 - i / 50f;
            assertEquals(12 - y, SoleContact.lowering(pose(y, 0), pose(y, 0)), 1e-5);
        }
    }

    @Test void missingHiddenAndExtremePosesCannotSinkTheModel() {
        assertEquals(0, SoleContact.lowering(null, pose(10, 0)));
        float[] hidden = pose(10, 0);
        hidden[6] = 0;
        assertEquals(0, SoleContact.lowering(hidden, pose(10, 0)));
        assertEquals(4, SoleContact.lowering(pose(0, 1), pose(0, 1)));
    }
}
