package strm.emfcompat.animationadditions.interaction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContactAimTest {
    @Test void fadeUsesOneBlendAndLevelsPalmRoll() {
        float[] base = {.2f, .4f, .3f};
        float[] target = ContactAim.rotation(base, 0, 0, -12, 1);
        float[] half = ContactAim.rotation(base, 0, 0, -12, .5f);
        assertEquals((base[0] + target[0]) * .5f, half[0], 1e-6);
        assertEquals((base[1] + target[1]) * .5f, half[1], 1e-6);
        assertEquals(.15f, half[2], 1e-6);
        assertArrayEquals(base, ContactAim.rotation(base, 0, 0, -12, 0), 1e-6f);
        assertEquals(0, target[2]);
    }
    @Test void yawTakesShortRouteAndZeroDirectionKeepsBase() {
        float[] base = {0, 3.12f, 0};
        float[] turn = ContactAim.rotation(base, .02f, 1, 1, .5f);
        assertTrue(Math.abs(turn[1] - base[1]) < .05f);
        assertArrayEquals(base, ContactAim.rotation(base, 0, 0, 0, 1));
    }
    @Test void crouchedContactStanceIsSmallerAndPlantsNeverDemandSpread() {
        var standing = ContactStance.forContact("Furniture", 8, 1, false);
        var crouched = ContactStance.forContact("Furniture", 8, 1, true);
        assertTrue(crouched.pitch() < standing.pitch());
        assertTrue(crouched.side() < standing.side());
        assertTrue(crouched.spread() < standing.spread());
        assertEquals(0, ContactStance.forContact("PlantReach", 8, 1, false).spread());
        var absent = ContactStance.forContact("Furniture", 8, 0, false);
        assertEquals(0, absent.pitch(), 1e-6);
        assertEquals(0, absent.side(), 1e-6);
        assertEquals(0, absent.forward(), 1e-6);
    }
}
