package strm.emfcompat.animationadditions.fright;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrightMotionTest {

    private static final int[] VARIANTS = {FrightMotion.RECOIL, FrightMotion.JUMP, FrightMotion.FREEZE};

    @Test
    void theJoltRunsOutAndTheRestGoesWithTheFeet() {
        for (int variant : VARIANTS) {
            for (int level = FrightMotion.LIGHT; level <= FrightMotion.STRONG; level++) {
                // Long after the jolt, with the feet home again, nothing is left.
                FrightMotion.Pose home = FrightMotion.pose(variant, level, FrightMotion.kept(level) + 1f, 0f, -1f);
                assertEquals(0f, Math.abs(home.yaw()) + Math.abs(home.bow()) + home.shrug() + home.armsUp() + home.armsOut()
                        + Math.abs(home.glance()) + home.duck() + home.round() + home.hop(), 1e-4f);
                // At the very start nothing has moved yet.
                FrightMotion.Pose start = FrightMotion.pose(variant, level, 0f, 0f, -1f);
                assertEquals(0f, Math.abs(start.yaw()) + start.shrug() + start.hop(), 1e-4f);
                // In the jolt the shoulders are up whatever the feet do.
                assertTrue(FrightMotion.pose(variant, level, 0.12f, 0f, -1f).shrug() > 0.7f);
            }
        }
    }

    @Test
    void theBodyIsNeverAheadOfTheFeet() {
        for (int variant : VARIANTS) {
            FrightMotion.Pose none = FrightMotion.pose(variant, FrightMotion.MEDIUM, 1.2f, 0f, -1f), half = FrightMotion.pose(variant, FrightMotion.MEDIUM, 1.2f, 0.5f, -1f),
                    all = FrightMotion.pose(variant, FrightMotion.MEDIUM, 1.2f, 1f, -1f);
            assertEquals(0f, Math.abs(none.bow()) + none.armsOut() + none.round() + none.duck(), 0.03f);
            assertTrue(all.shrug() > half.shrug() && half.shrug() > none.shrug());
        }
    }

    @Test
    void recoilGoesBackFromTheSoundWithBothFeetAndLeansAway() {
        // The sound ahead (-z): back is +z; the right foot leads, the left follows.
        assertTrue(FrightMotion.foot(FrightMotion.RECOIL, FrightMotion.LIGHT, true, 0f, -1f).z > 1.5f);
        assertTrue(FrightMotion.foot(FrightMotion.RECOIL, FrightMotion.LIGHT, false, 0f, -1f).z > 0.6f);
        // To the right (-x): the left foot leads, to the left.
        assertTrue(FrightMotion.foot(FrightMotion.RECOIL, FrightMotion.LIGHT, false, -1f, 0f).x > 1.5f);
        // Leaning back from a sound ahead, forward from one behind.
        assertTrue(FrightMotion.pose(FrightMotion.RECOIL, FrightMotion.LIGHT, 0.6f, 1f, -1f).bow() < 0f);
        assertTrue(FrightMotion.pose(FrightMotion.RECOIL, FrightMotion.LIGHT, 0.6f, 1f, 1f).bow() > 0f);
        assertTrue(FrightMotion.pose(FrightMotion.RECOIL, FrightMotion.LIGHT, 0.6f, 1f, -1f).round() > 0.3f);
    }

    @Test
    void jumpLeavesTheGroundAndLandsWider() {
        assertTrue(FrightMotion.pose(FrightMotion.JUMP, FrightMotion.LIGHT, 0.15f, 0f, -1f).hop() > 0.8f);
        assertEquals(0f, FrightMotion.pose(FrightMotion.JUMP, FrightMotion.LIGHT, 0.5f, 1f, -1f).hop(), 1e-6f);
        assertTrue(FrightMotion.lands(FrightMotion.JUMP, FrightMotion.LIGHT));
        assertTrue(FrightMotion.foot(FrightMotion.JUMP, FrightMotion.LIGHT, true, 0f, -1f).x < -0.5f);
        assertTrue(FrightMotion.foot(FrightMotion.JUMP, FrightMotion.LIGHT, false, 0f, -1f).x > 0.5f);
        // The look goes to one side and then the other.
        assertTrue(FrightMotion.pose(FrightMotion.JUMP, FrightMotion.LIGHT, 0.5f, 1f, -1f).glance()
                * FrightMotion.pose(FrightMotion.JUMP, FrightMotion.LIGHT, 1.05f, 1f, -1f).glance() < 0f);
    }

    @Test
    void freezeKeepsTheFeetForAStartAndTakesOneLook() {
        assertEquals(0f, FrightMotion.foot(FrightMotion.FREEZE, FrightMotion.LIGHT, true, 0f, -1f).length()
                + FrightMotion.foot(FrightMotion.FREEZE, FrightMotion.LIGHT, false, 0f, -1f).length(), 1e-6f);
        assertEquals(0f, FrightMotion.hop(FrightMotion.FREEZE, FrightMotion.LIGHT), 1e-6f);
        assertTrue(FrightMotion.pose(FrightMotion.FREEZE, FrightMotion.LIGHT, 0.5f, 1f, -1f).round() > 0.4f);
        assertEquals(0f, FrightMotion.pose(FrightMotion.FREEZE, FrightMotion.LIGHT, FrightMotion.kept(FrightMotion.LIGHT), 1f, -1f).round(), 1e-4f);
    }
}
