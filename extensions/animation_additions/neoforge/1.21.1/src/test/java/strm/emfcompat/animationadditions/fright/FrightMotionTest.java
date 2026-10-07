package strm.emfcompat.animationadditions.fright;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrightMotionTest {

    @Test
    void theShakeRunsOutAndTheRestGoesWithTheFeet() {
        for (int level = FrightMotion.LIGHT; level <= FrightMotion.STRONG; level++) {
            // Long after the shake, with the feet home again, nothing is left.
            FrightMotion.Pose home = FrightMotion.pose(level, FrightMotion.kept(level) + 1f, 0f, 0f, -1f);
            assertEquals(0f, Math.abs(home.yaw()) + Math.abs(home.bow()) + home.armsUp() + home.armsOut() + Math.abs(home.against())
                    + Math.abs(home.nod()) + home.round() + home.hop(), 1e-4f);
            // At the very start nothing has moved yet.
            FrightMotion.Pose start = FrightMotion.pose(level, 0f, 0f, 0f, -1f);
            assertEquals(0f, Math.abs(start.yaw()) + start.armsOut() + start.hop(), 1e-4f);
            // In the shake the arms are out from the body whatever the feet do.
            assertTrue(FrightMotion.pose(level, FrightMotion.shake(level) * 0.25f, 0f, 0f, -1f).armsOut() > 0.1f);
        }
    }

    @Test
    void theBodyIsNeverAheadOfTheFeet() {
        float late = FrightMotion.shake(FrightMotion.MEDIUM) + 0.05f;
        FrightMotion.Pose none = FrightMotion.pose(FrightMotion.MEDIUM, late, 0f, 0f, -1f), half = FrightMotion.pose(FrightMotion.MEDIUM, late, 0.5f, 0f, -1f),
                all = FrightMotion.pose(FrightMotion.MEDIUM, late, 1f, 0f, -1f);
        assertEquals(0f, Math.abs(none.bow()) + none.round(), 1e-3f);
        assertTrue(all.round() > half.round() && half.round() > none.round());
        assertTrue(Math.abs(all.bow()) > Math.abs(half.bow()));
    }

    @Test
    void bothFeetGoBackFromTheSoundAndTheBodyLeansAway() {
        // The sound ahead (-z): back is +z; the right foot leads, the left follows.
        assertTrue(FrightMotion.foot(FrightMotion.LIGHT, true, 0f, -1f).z > 1.5f);
        assertTrue(FrightMotion.foot(FrightMotion.LIGHT, false, 0f, -1f).z > 0.6f);
        // To the right (-x): the left foot leads, to the left.
        assertTrue(FrightMotion.foot(FrightMotion.LIGHT, false, -1f, 0f).x > 1.5f);
        // Leaning back from a sound ahead, forward from one behind, and looking at it.
        assertTrue(FrightMotion.pose(FrightMotion.LIGHT, 1.1f, 1f, 0f, -1f).bow() < 0f);
        assertTrue(FrightMotion.pose(FrightMotion.LIGHT, 1.1f, 1f, 0f, 1f).bow() > 0f);
        assertTrue(FrightMotion.pose(FrightMotion.LIGHT, 0.6f, 1f, 0f, -1f).round() > 0.3f);
    }

    @Test
    void theArmsMoveOnceAndAreThenHeldOnGuard() {
        float late = FrightMotion.shake(FrightMotion.LIGHT) * 0.6f;
        // After the first jerk: out from the body and before it by the stand, with no swinging - whatever foot is stepping.
        FrightMotion.Pose held = FrightMotion.pose(FrightMotion.LIGHT, late, 1f, 0f, -1f), stepping = FrightMotion.pose(FrightMotion.LIGHT, late, 1f, 1f, -1f);
        assertTrue(held.armsOut() > 0.15f && held.armsUp() > 0.1f);
        assertEquals(0f, Math.abs(held.against()) + Math.abs(held.sway()), 0.012f);
        assertEquals(held.against(), stepping.against(), 1e-6f);
        // The chest still goes against the hips with a step.
        assertTrue(stepping.yaw() < held.yaw());
        // The first jerk is there, and the arms are out before the feet have moved.
        assertTrue(FrightMotion.pose(FrightMotion.LIGHT, 0.2f, 0f, 0f, -1f).armsOut() > 0.15f);
        // They come down with the feet.
        assertEquals(0f, FrightMotion.pose(FrightMotion.LIGHT, 1.4f, 0f, 0f, -1f).armsOut(), 1e-4f);
    }

    @Test
    void onlyTheWorseOnesLeaveTheGround() {
        assertEquals(0f, FrightMotion.hop(FrightMotion.LIGHT), 1e-6f);
        assertEquals(0f, FrightMotion.pose(FrightMotion.LIGHT, 0.15f, 0f, 0f, -1f).hop(), 1e-6f);
        assertTrue(FrightMotion.pose(FrightMotion.MEDIUM, 0.15f, 0f, 0f, -1f).hop() > 0.8f);
        assertTrue(FrightMotion.pose(FrightMotion.STRONG, 0.15f, 0f, 0f, -1f).hop() > FrightMotion.pose(FrightMotion.MEDIUM, 0.15f, 0f, 0f, -1f).hop());
    }
}
