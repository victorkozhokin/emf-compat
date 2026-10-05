package strm.emfcompat.animationadditions.wallhand;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WallPoseMathTest {
    @Test void equalWallDistancesCannotReverseAnEngagedPose() {
        float side = WallPoseMath.side(1, 1.3f, 1.2f, false);
        for (int i = 0; i < 100; i++)
            side = WallPoseMath.side(side, 1.25f + (i % 2 == 0 ? .04f : -.04f), 1.25f, true);
        assertEquals(1, side);
        assertEquals(-1, WallPoseMath.side(side, .2f, 1.2f, false), "reselect after leaving the gap");
    }

    @Test void aimsReconstructFrontAndRearWallContactsWithoutStretching() {
        for (float across = -9; across <= 9; across += .5f)
            for (float direction : new float[]{-1, 1}) {
                float along = direction * (float)Math.sqrt(100 - across * across - 9);
                var aim = WallPoseMath.aim(across, 3, along);
                var reconstructed = new Quaternionf().rotationZYX(aim.roll(), 0, aim.pitch())
                        .transform(new Vector3f(0, 10, 0));
                assertEquals(across, reconstructed.x, 1e-5);
                assertEquals(3, reconstructed.y, 1e-5);
                assertEquals(along, reconstructed.z, 1e-5);
            }
        assertNull(WallPoseMath.aim(0, 0, 0));
        assertNull(WallPoseMath.aim(Float.NaN, 3, 2));
    }

    @Test void wallEndingReleasesTheHandContinuouslyAndAllowsReacquisition() {
        var contact = new WallPoseMath.Contact();
        var aim = new WallPoseMath.Aim(1.1f, .3f);
        for (int i = 0; i < 120; i++) contact.update(aim, 1, 1.0 / 60);
        float previous = contact.weight;
        contact.update(null, 0, 1.0 / 60);
        assertTrue(contact.weight > 0 && contact.weight < previous);
        assertEquals(aim.pitch(), contact.pitch, 1e-5, "fade from the last contact instead of snapping");
        for (int i = 0; i < 60; i++) contact.update(null, 0, 1.0 / 60);
        assertFalse(contact.known);
        contact.update(new WallPoseMath.Aim(-1, -.2f), 1, 1.0 / 60);
        assertTrue(contact.weight > 0 && contact.weight < .2f);
        assertEquals(-1, contact.pitch, 1e-5);
    }

    @Test void contactFadeHasTheSameTimingAtThirtyAndOneHundredFortyFourFps() {
        float[] weights = new float[2];
        int index = 0;
        for (int fps : new int[]{30, 144}) {
            var contact = new WallPoseMath.Contact();
            for (int i = 0; i < fps; i++) contact.update(new WallPoseMath.Aim(1, 0), 1, 1.0 / fps);
            for (int i = 0; i < fps; i++) contact.update(null, 0, 1.0 / fps);
            weights[index++] = contact.weight;
        }
        assertEquals(weights[0], weights[1], 1e-6);
    }

    @Test void angleWrapTakesTheShortPathAndEntryDoesNotSnap() {
        assertEquals(Math.PI, WallPoseMath.followAngle(3.1f, -3.1f, .5f), 1e-5);
        var contact = new WallPoseMath.Contact();
        contact.update(new WallPoseMath.Aim(1, 0), 1, 0);
        assertEquals(0, contact.weight);
    }

    @Test void clearanceBlendIsBoundedMonotoneAndSmoothAtItsEnds() {
        float previous = 0;
        for (float v = -.5f; v < 1.5f; v += .01f) {
            float eased = WallPoseMath.ease(v);
            assertTrue(eased >= previous && eased >= 0 && eased <= 1);
            previous = eased;
        }
        assertTrue(WallPoseMath.ease(.001f) < .00001f);
        assertTrue(1 - WallPoseMath.ease(.999f) < .00001f);
    }
}
