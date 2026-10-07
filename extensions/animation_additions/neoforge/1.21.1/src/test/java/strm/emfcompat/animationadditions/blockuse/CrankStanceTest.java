package strm.emfcompat.animationadditions.blockuse;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;
import strm.emfcompat.animationadditions.interaction.Ease;
class CrankStanceTest {
    @Test void wrapDoesNotMistakeFullTurnForReversal() {
        assertEquals(2, CrankStanceMath.delta(359, 1));
        assertEquals(-2, CrankStanceMath.delta(1, 359));
        assertEquals(2, CrankStanceMath.delta(719, 721));
    }
    @Test void reversalChangesStaggerButKeepsWideStance() {
        for (boolean crouch : new boolean[]{false, true}) {
            Vector3f r = CrankStanceMath.stance(true, crouch, 1, true), l = CrankStanceMath.stance(false, crouch, 1, true);
            assertTrue(r.x < 0 && l.x > 0);
            assertEquals(-r.z, l.z);
            Vector3f reversed = CrankStanceMath.stance(true, crouch, -1, true);
            assertEquals(r.x, reversed.x);
            assertEquals(-r.z, reversed.z);
            assertEquals(new Vector3f(), CrankStanceMath.stance(true, crouch, 1, false));
        }
    }
    @Test void SetupStepHasZeroEndpointLiftAndSmoothEndpointVelocity() {
        assertEquals(0, Ease.smooth(0));
        assertEquals(1, Ease.smooth(1));
        assertEquals(0, CrankStanceMath.lift(0), 1e-6);
        assertEquals(0, CrankStanceMath.lift(1), 1e-6);
        assertTrue(Ease.smooth(.001f) < .00001f);
        assertTrue(1 - Ease.smooth(.999f) < .00001f);
        assertEquals(1, CrankStanceMath.lift(.5f), 1e-6);
    }
}
