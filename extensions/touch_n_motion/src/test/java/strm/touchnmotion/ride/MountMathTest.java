package strm.touchnmotion.ride;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MountMathTest {
    /** Where the middle of the foot ends, rider pixels from the middle. */
    private static float foot(float[] legs) {
        return legs[0] + MountMath.LEG * (float) Math.sin(legs[1]);
    }

    @Test void theFootEndsJustPastTheBarrelOnEveryMountTheHipsCanReach() {
        for (float halfWidth : new float[]{4.3f, 4.6f, 4.9f, 5.3f, 5.87f}) {
            float[] legs = MountMath.legs(halfWidth);
            assertEquals(halfWidth + MountMath.FOOT_OUT, foot(legs), 0.05f, "half width " + halfWidth);
            assertTrue(legs[0] <= MountMath.HIP_MOST && legs[0] >= MountMath.HIP);
        }
    }

    @Test void theHipsNeverGoPastTheTorsoAndTheLegsLieInLessThanTheOldPose() {
        for (float halfWidth = 3f; halfWidth <= 8f; halfWidth += 0.25f) {
            float[] legs = MountMath.legs(halfWidth);
            assertTrue(legs[0] <= MountMath.HIP_MOST);
            // The old pose: hips at 2.9 px, legs turned out 0.5. On a wide barrel the leg lies in it less now;
            // on a narrow one the foot no longer stands off its side.
            if (halfWidth >= 4.4f) assertTrue(MountMath.embedding(halfWidth, legs[0]) <= MountMath.embedding(halfWidth, 2.9f) + 1e-4f);
            else assertTrue(Math.abs(foot(legs) - halfWidth) < Math.abs(2.9f + MountMath.LEG * (float) Math.sin(0.5) - halfWidth));
            assertTrue(legs[1] >= MountMath.ROLL_LEAST - 1e-4f && legs[1] <= MountMath.ROLL_MOST + 1e-4f);
        }
    }

    @Test void theReinsAreWhereTheyWereOnAVanillaHorse() {
        assertEquals(1f, MountMath.reinsScale(MountMath.VANILLA_HALF_WIDTH), 1e-5f);
        assertEquals(0.8f, MountMath.reinsScale(1f), 1e-5f);
        assertEquals(1.25f, MountMath.reinsScale(20f), 1e-5f);
    }
}
