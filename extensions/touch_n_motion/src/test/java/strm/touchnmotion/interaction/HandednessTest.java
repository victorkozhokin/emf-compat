package strm.touchnmotion.interaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HandednessTest {
    private static boolean pick(float x, float z, boolean mainRight, Boolean held) {
        return Handedness.right(x, z, mainRight, held, false, false, true, true, true);
    }

    @Test void straightAheadIsTheMainHand() {
        assertTrue(pick(0, -10, true, null));
        assertFalse(pick(0, -10, false, null));
        // A little to the other side is still the main hand's.
        assertTrue(pick(1.5f, -10, true, null));
        assertFalse(pick(-1.5f, -10, false, null));
    }

    @Test void outToTheOtherSideIsThatSidesHand() {
        assertFalse(pick(6, -10, true, null));
        assertTrue(pick(-6, -10, false, null));
        // Beside the body.
        assertFalse(pick(8, 0, true, null));
    }

    @Test void aHeldPointIsHandedOverOnlyFurtherRound() {
        // 15 degrees to the left: a fresh point is the left hand's...
        float x = (float) Math.tan(Math.toRadians(15)) * 10;
        assertFalse(pick(x, -10, true, null));
        // ...but the right hand holding it keeps it.
        assertTrue(pick(x, -10, true, true));
        // Back to 5 degrees right of ahead the left hand still keeps it.
        assertFalse(pick(-(float) Math.tan(Math.toRadians(5)) * 10, -10, true, false));
    }

    @Test void nearestOffIsAlwaysTheMainHand() {
        assertTrue(Handedness.right(8, -2, true, null, false, false, true, true, false));
        assertFalse(Handedness.right(-8, -2, false, null, false, false, true, true, false));
    }

    @Test void aBusyHandGivesWayToAFreeOne() {
        assertTrue(Handedness.right(6, -10, true, null, false, true, true, true, true));
        // Both taken: the side decides, the arbiter the rest.
        assertFalse(Handedness.right(6, -10, true, null, true, true, true, true, true));
    }

    @Test void aHandOutOfReachGivesWayToOneInReach() {
        assertTrue(Handedness.right(6, -10, true, null, false, false, true, false, true));
        assertFalse(Handedness.right(0, -10, true, null, false, false, false, true, true));
    }
}
