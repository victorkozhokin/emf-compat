package strm.touchnmotion.interaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactStanceTest {
    @Test
    void aSourceWithNoStanceAsksForNothing() {
        ContactStance none = ContactStance.forContact("ButtonPress", 6, 1, false);
        assertEquals(0, none.pitch());
        assertEquals(0, none.side());
        assertEquals(0, none.spread());
    }

    @Test
    void theStanceGrowsWithTheContactAndIsNothingWithoutIt() {
        ContactStance off = ContactStance.forContact("Furniture", 6, 0, false);
        ContactStance half = ContactStance.forContact("Furniture", 6, .5f, false);
        ContactStance full = ContactStance.forContact("Furniture", 6, 1, false);
        assertEquals(0, off.pitch());
        assertEquals(0, off.side());
        assertTrue(half.pitch() > 0 && half.pitch() < full.pitch());
        assertTrue(Math.abs(half.forward()) < Math.abs(full.forward()));
    }

    @Test
    void theWeightGoesToTheSideOfTheContactAndNoFurtherThanItsLimit() {
        ContactStance right = ContactStance.forContact("DoorHold", -6, 1, false);
        ContactStance left = ContactStance.forContact("DoorHold", 6, 1, false);
        ContactStance far = ContactStance.forContact("DoorHold", 60, 1, false);
        assertEquals(-right.side(), left.side(), 1e-6f);
        assertTrue(left.side() > 0);
        assertEquals(ContactStance.forContact("DoorHold", 8, 1, false).side(), far.side(), 1e-6f);
    }

    @Test
    void crouchingMakesItSmaller() {
        ContactStance standing = ContactStance.forContact("WallHand", 5, 1, false);
        ContactStance crouching = ContactStance.forContact("WallHand", 5, 1, true);
        assertTrue(crouching.pitch() < standing.pitch());
        assertTrue(Math.abs(crouching.side()) < Math.abs(standing.side()));
        assertTrue(crouching.spread() < standing.spread());
    }

    @Test
    void brushingPlantsNeverStepsTheFeetApart() {
        assertEquals(0, ContactStance.forContact("PlantReach", 5, 1, false).spread());
    }

    @Test
    void anOverfullWeightIsTakenAsOne() {
        assertEquals(ContactStance.forContact("Furniture", 4, 1, false).pitch(), ContactStance.forContact("Furniture", 4, 3, false).pitch(), 1e-6f);
    }
}
