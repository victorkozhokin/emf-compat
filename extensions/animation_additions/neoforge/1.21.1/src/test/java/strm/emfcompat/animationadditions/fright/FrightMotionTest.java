package strm.emfcompat.animationadditions.fright;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrightMotionTest {

    private static FrightMotion.Pose at(int level, float share) {
        return FrightMotion.pose(level, FrightMotion.seconds(level) * share);
    }

    @Test
    void comesFromNothingAndRunsOutToNothing() {
        for (int level = FrightMotion.LIGHT; level <= FrightMotion.STRONG; level++) {
            for (FrightMotion.Pose pose : new FrightMotion.Pose[]{FrightMotion.pose(level, 0f), at(level, 0.999f), at(level, 1.2f)}) {
                assertEquals(0f, Math.abs(pose.yaw()) + pose.shrug() + pose.armsUp() + Math.abs(pose.glance()) + pose.duck() + pose.round() + pose.hop(), 2e-2f);
            }
            assertTrue(at(level, 0.3f).shrug() > 0.9f);
        }
    }

    @Test
    void eachHasWhatTheOneBeforeHasAndMore() {
        FrightMotion.Pose light = at(FrightMotion.LIGHT, 0.3f), medium = at(FrightMotion.MEDIUM, 0.3f), strong = at(FrightMotion.STRONG, 0.3f);
        assertTrue(light.shrug() < medium.shrug() && medium.shrug() < strong.shrug());
        // A start only shudders and looks about; a scare ducks and lets the arms off the body; a terror looks round at the sound.
        assertEquals(0f, light.armsOut() + light.round(), 1e-6f);
        // In the middle of every one the hands come a little forward and go from side to side.
        assertTrue(light.armsUp() > 0.05f && at(FrightMotion.LIGHT, 0.25f).sway() * at(FrightMotion.LIGHT, 0.7f).sway() < 0f);
        assertTrue(Math.abs(at(FrightMotion.LIGHT, 0.25f).glance()) > 0.1f);
        assertTrue(light.bow() > 0f && light.bow() < medium.bow() && medium.bow() < strong.bow());
        assertTrue(medium.duck() > 0.15f && medium.armsUp() > 0.1f && medium.armsOut() > 0.05f);
        assertEquals(0f, medium.round(), 1e-6f);
        assertTrue(strong.round() > 0.9f && strong.duck() > medium.duck());
        assertTrue(FrightMotion.seconds(FrightMotion.LIGHT) < FrightMotion.seconds(FrightMotion.MEDIUM)
                && FrightMotion.seconds(FrightMotion.MEDIUM) < FrightMotion.seconds(FrightMotion.STRONG));
    }

    @Test
    void theJumpIsOnlyAtTheStartOfAScareAndATerror() {
        assertEquals(0f, FrightMotion.pose(FrightMotion.LIGHT, 0.1f).hop(), 1e-6f);
        assertTrue(FrightMotion.pose(FrightMotion.MEDIUM, 0.15f).hop() > 1f);
        assertTrue(FrightMotion.pose(FrightMotion.STRONG, 0.15f).hop() > FrightMotion.pose(FrightMotion.MEDIUM, 0.15f).hop());
        assertEquals(0f, FrightMotion.pose(FrightMotion.MEDIUM, 0.4f).hop(), 1e-6f);
    }

    @Test
    void theLookGoesToOneSideAndThenTheOther() {
        assertTrue(at(FrightMotion.LIGHT, 0.25f).glance() * at(FrightMotion.LIGHT, 0.7f).glance() < 0f);
    }

    @Test
    void theWaryStandIsBackFromTheSoundAndGivenUpBeforeTheEnd() {
        // The sound to the right (-x): away is to the left, and the left foot goes.
        assertTrue(FrightMotion.foot(FrightMotion.MEDIUM, false, -1f, 0f).x > 2f);
        // The other follows, less far - both feet are in it, at every level.
        assertTrue(FrightMotion.foot(FrightMotion.MEDIUM, true, -1f, 0f).x > 0.5f);
        for (int level = FrightMotion.LIGHT; level <= FrightMotion.STRONG; level++)
            assertTrue(FrightMotion.foot(level, true, 0f, -1f).length() > 0.6f && FrightMotion.foot(level, false, 0f, -1f).length() > 0.6f);
        // Ahead (-z): back is +z, both feet in a terror.
        assertTrue(FrightMotion.foot(FrightMotion.STRONG, true, 0f, -1f).z > 3f && FrightMotion.foot(FrightMotion.STRONG, false, 0f, -1f).z > 1f);
        assertTrue(FrightMotion.wary(FrightMotion.MEDIUM, 0.5f));
        assertTrue(!FrightMotion.wary(FrightMotion.MEDIUM, FrightMotion.seconds(FrightMotion.MEDIUM) * 0.9f));
    }

    @Test
    void theShudderRunsDown() {
        float early = 0f, late = 0f;
        for (int i = 0; i < 100; i++) {
            early = Math.max(early, Math.abs(at(FrightMotion.MEDIUM, 0.05f + 0.2f * i / 100f).yaw()));
            late = Math.max(late, Math.abs(at(FrightMotion.MEDIUM, 0.55f + 0.2f * i / 100f).yaw()));
        }
        assertTrue(early > 0.05f && late < early * 0.6f);
    }
}
