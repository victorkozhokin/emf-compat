package strm.emfcompat.animationadditions.footgrounding.compat;

import org.junit.jupiter.api.Test;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static org.junit.jupiter.api.Assertions.*;

class FootStepTest {
    @Test void predictionFindsFirstStepInsteadOfRejectingDistantWall() {
        assertEquals(0.5, FootStep.predictAlongPath(0, t -> t < 0.3 ? 0 : t < 0.7 ? 0.5 : 1));
        assertEquals(0, FootStep.predictAlongPath(0, t -> 1));
        assertEquals(0, FootStep.predictAlongPath(0, t -> Double.NaN));
        assertEquals(-0.5, FootStep.predictAlongPath(0, t -> -0.5));
    }

    @Test void anticipationIsAdditiveAndVanishesOnFlatOrAfterReset() {
        FootStep step = new FootStep();
        step.update(0.5f, 0, 0, () -> 0);
        step.update(0.4f, 0.02, 0, () -> 0.5);
        step.update(-0.05f, 0.1, 0, () -> 0.5);
        assertTrue(step.anticipation() > 0.5f);
        step.reset();
        assertEquals(0, step.anticipation());
        step.update(0.5f, 0, 0, () -> 0);
        step.update(0.4f, 0.02, 0, () -> 0);
        step.update(-0.05f, 0.1, 0, () -> 0);
        assertEquals(0, step.anticipation());
    }

    @Test void plantedContactCannotSinkIntoSlabAfterHitboxStepsUp() {
        FootStep step = new FootStep();
        step.update(0.2f, 0, 150, () -> 150);
        assertEquals(150.5, step.update(0.3f, 0.05, 150.5, () -> 150.5), 1e-6);
        assertEquals(150.5, step.plantedY, 1e-6);
    }

    @Test void contactHoldsOverDropUntilSwing() {
        FootStep step = new FootStep();
        step.update(0.2f, 0, 150.5, () -> 150.5);
        assertEquals(150.5, step.update(0.3f, 0.05, 150, () -> 150), 1e-6);
    }

    @Test void backwardLandingIsBehindBody() {
        assertTrue(Math.sin(FootStep.landingPitch(0.5f, -1)) > 0);
        assertTrue(Math.sin(FootStep.landingPitch(0.5f, 1)) < 0);
    }

    @Test void uphillClearsLipBeforeEndOfSwingAndSettlesWithoutPop() {
        assertTrue(FootStep.swingHeight(0, 0.5, 0.65) >= 0.5);
        assertEquals(0, FootStep.swingHeight(0, 0.5, 0), 1e-8);
        assertEquals(0.5, FootStep.swingHeight(0, 0.5, 1), 1e-8);
        assertEquals(0.5, FootStep.swingHeight(0, 0.5, 0.9999), 1e-6);
        for (int i = 0; i <= 100; i++) {
            double y = FootStep.swingHeight(0, 0.5, i / 100.0);
            assertTrue(y >= 0 && y <= 0.5 + 1.5 * 0.9375 / 16);
        }
    }

    @Test void flatAndDescendingGaitsReceiveNoUpwardArc() {
        for (int i = 0; i <= 100; i++) {
            double t = i / 100.0;
            assertEquals(4, FootStep.swingHeight(4, 4, t));
            double y = FootStep.swingHeight(4, 3.5, t);
            assertTrue(y <= 4 && y >= 3.5);
        }
    }

    @Test void lateVisibleRiseIsNotLockedOutByHalfSwing() {
        FootStep step = new FootStep();
        step.update(0.5f, 0, 0, () -> 0);
        step.update(0.4f, 0.02, 0, () -> 0);
        step.update(-0.2f, 0.15, 0, () -> 0);
        assertTrue(step.progress > 0.5);
        step.update(-0.3f, 0.2, 0, () -> 0.5);
        assertEquals(0.5, step.landingY);
    }

    @Test void wallCliffAndMissingPredictionCannotBecomeLanding() {
        for (double floor : new double[]{1, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            FootStep step = new FootStep();
            step.update(0.5f, 0, 0, () -> 0);
            step.update(0.4f, 0.02, 0, () -> floor);
            assertEquals(0, step.landingY);
        }
    }

    @Test void airborneOrTeleportResetDiscardsOldWorldHeightAndTiming() {
        FootStep step = new FootStep();
        step.update(0.5f, 0, 150, () -> 150);
        step.update(0.4f, 0.02, 150, () -> 150.5);
        step.reset();
        assertFalse(step.known);
        assertFalse(step.swinging);
        assertEquals(200, step.update(-0.4f, 20, 200, () -> 200));
        assertFalse(step.swinging);
        assertEquals(0.25, step.swingSeconds);
    }

    @Test void additiveLiftMatchesRenderedSoleForForwardBackwardAndRolledLegs() {
        for (float pitch : new float[]{-0.8f, 0, 0.8f}) {
            for (float yaw : new float[]{-0.3f, 0.3f}) {
                for (float roll : new float[]{-0.2f, 0.2f}) {
                    float dp = -0.2f, dr = 0.1f, rise = 5;
                    Vector3f before = new Quaternionf().rotationZYX(roll, yaw, pitch).transform(new Vector3f(0, 12, 0));
                    Vector3f after = new Quaternionf().rotationZYX(roll + dr, yaw, pitch + dp).transform(new Vector3f(0, 12, 0));
                    float lift = rise - FootStep.rotationLift(pitch, yaw, roll, dp, dr, 12);
                    assertEquals(rise, before.y - (after.y - lift), 2e-6);
                }
            }
        }
    }
}
