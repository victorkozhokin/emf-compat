package strm.touchnmotion.pocket;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import strm.touchnmotion.interaction.Skeleton;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocketMotionTest {
    private static Vector3f hand(float[] arm) {
        return new Quaternionf().rotationZYX(arm[2], arm[1], arm[0])
                .transform(new Vector3f(0, Skeleton.ARM_TO_FINGERTIPS, 0)).add(Skeleton.RIGHT_SHOULDER).add(0, arm[3], 0);
    }

    @Test
    void theHandGoesUpInFrontAndRoundTheSideIntoTheBackPocket() {
        float highest = 99, front = 0, outermost = 0;
        for (int i = 0; i <= 100; i++) {
            Vector3f at = hand(PocketMotion.arm(i / 100f * PocketMotion.OVER_POCKET));
            highest = Math.min(highest, at.y);
            front = Math.min(front, at.z);
            outermost = Math.min(outermost, at.x);
        }
        assertTrue(highest < 10.5f && front < -5, "up and forward on the way: y " + highest + " z " + front);
        assertTrue(outermost < -8, "round the outside on the way down: x " + outermost);
        Vector3f pocket = hand(PocketMotion.arm(PocketMotion.IN_POCKET));
        // The torso is 4 px deep about z 0 and the right hip is at x -1.9.
        assertTrue(pocket.z > 2.5f && pocket.z < 5, "behind the body: " + pocket);
        assertTrue(pocket.x > -5 && pocket.x < -2, "over the right hip, not across the back: " + pocket);
        assertTrue(pocket.y > 11.5f && pocket.y < 13.5f, "at hip height: " + pocket);
    }

    @Test
    void theHandArrivesRaisedGoesDownIntoThePocketAndIsLiftedOut() {
        float over = hand(PocketMotion.arm(PocketMotion.OVER_POCKET)).y, in = hand(PocketMotion.arm(PocketMotion.IN_POCKET)).y,
                out = hand(PocketMotion.arm(PocketMotion.LEAVES)).y;
        assertTrue(in - over > 2, "down into it: " + over + " -> " + in);
        assertTrue(in - out > 1.5f, "and up out of it: " + in + " -> " + out);
        assertEquals(0, PocketMotion.arm(1)[3], 1e-6);
    }

    @Test
    void theHandComesInOnlyBehindTheBody() {
        // Going there and coming back: inside the line of the shoulder only once past the back.
        for (int i = 0; i <= 400; i++) {
            Vector3f at = hand(PocketMotion.arm(i / 400f));
            assertTrue(at.z > 2.5f || at.x <= Skeleton.RIGHT_SHOULDER.x + .5f, "through the torso at " + i / 400f + ": " + at);
        }
    }

    @Test
    void theHandDoesNotStopOnTheWay() {
        // One sweep: between leaving and arriving the hand keeps moving.
        for (int i = 5; i < 35; i++) {
            float a = i / 100f, b = (i + 1) / 100f;
            assertTrue(hand(PocketMotion.arm(a)).distance(hand(PocketMotion.arm(b))) > .02f, "stalls at " + a);
        }
    }

    @Test
    void itEndsInThePoseItLeft() {
        // Nothing is left for the release to undo: the arm hangs, the head looks where it looked.
        assertEquals(0, PocketMotion.arm(1)[0], 1e-6);
        assertEquals(0, PocketMotion.arm(1)[1], 1e-6);
        assertEquals(0, PocketMotion.head(0)[0], 1e-6);
        assertEquals(0, PocketMotion.head(1)[0], 1e-6);
        assertEquals(0, PocketMotion.head(1)[1], 1e-6);
        assertTrue(PocketMotion.head(.45f)[0] > .45f, "looks down at the pocket in between");
    }

    @Test
    void theHandComesBackTheWayItWentButLow() {
        float highest = 99, front = 0, outermost = 0;
        for (int i = 0; i <= 100; i++) {
            Vector3f at = hand(PocketMotion.arm(PocketMotion.LEAVES + i / 100f * (1 - PocketMotion.LEAVES)));
            highest = Math.min(highest, at.y);
            front = Math.min(front, at.z);
            outermost = Math.min(outermost, at.x);
        }
        assertTrue(front < -1.5f && outermost < -6.5f, "out round the side and forward past the hip: z " + front + " x " + outermost);
        // Lower than it went in: a hand walked back, not swung.
        assertTrue(highest > 10.5f, "kept low: y " + highest);
        // It sets off and arrives at rest: the first and the last hundredth of the way barely move.
        assertTrue(hand(PocketMotion.arm(.99f)).distance(hand(PocketMotion.arm(1))) < .1f);
    }

    @Test
    void theHandNeverJumps() {
        Vector3f before = hand(PocketMotion.arm(0));
        for (int i = 1; i <= 1000; i++) {
            Vector3f now = hand(PocketMotion.arm(i / 1000f));
            // A thousandth of the gesture is 1.6 ms; a quarter of a pixel in that would be a jump.
            assertTrue(now.distance(before) < .25f, "at " + i / 1000f + ": " + before + " -> " + now);
            before = now;
        }
    }

    @Test
    void theTorsoStartsAndEndsUpright() {
        assertEquals(0, PocketMotion.lean(0), 1e-6);
        assertEquals(0, PocketMotion.lean(1), 1e-6);
        float most = 0;
        for (int i = 0; i <= 100; i++) most = Math.max(most, PocketMotion.lean(i / 100f));
        assertTrue(most > .9f && most <= 1f, "bends fully in between: " + most);
    }

    @Test
    void theFeetHaveTimeToStepBack() {
        assertTrue(PocketMotion.apart(.3f));
        // Two steps of 0.32 s each fit between closing the stance and the end.
        assertTrue(!PocketMotion.apart(0) && !PocketMotion.apart(1 - (float) (.64 / PocketMotion.SECONDS)));
    }

    @Test
    void theArmIsLetGoOnItsWayDownNotAfterHangingStill() {
        Vector3f rest = hand(PocketMotion.arm(1)), pocket = hand(PocketMotion.arm(PocketMotion.LEAVES));
        float left = hand(PocketMotion.arm(PocketMotion.LETS_GO)).distance(rest) / pocket.distance(rest);
        // Still moving, so nothing hangs; and the head and the torso have nothing left for the release.
        assertTrue(left > .2f && left < .7f, "of the way still to go: " + left);
        assertEquals(0, PocketMotion.head(PocketMotion.LETS_GO)[0], 1e-6);
        assertEquals(0, PocketMotion.lean(PocketMotion.LETS_GO), 1e-6);
    }
}
