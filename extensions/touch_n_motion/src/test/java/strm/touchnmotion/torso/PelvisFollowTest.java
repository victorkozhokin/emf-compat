package strm.touchnmotion.torso;

import org.junit.jupiter.api.Test;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class PelvisFollowTest {
    @Test void gazeAndAuthoredHeadRollSurviveCombinedClearanceTurns() {
        Vector3f neck = new Vector3f(0, -2, 1), waist = new Vector3f(0, 12, -2);
        for (float pitch : new float[]{-.9f, 0, .7f})
            for (float yaw : new float[]{-1.4f, -.5f, .5f, 1.4f})
                for (float roll : new float[]{-.2f, 0, .2f})
                    for (float turnYaw : new float[]{-1.2f, 0, 1.2f}) {
                        Quaternionf turn = new Quaternionf().rotationZYX(.25f, turnYaw, .45f);
                        var head = PelvisFollow.carryGaze(neck, pitch, yaw, roll, turn, waist, 1.5f);
                        Vector3f expectedNeck = turn.transform(new Vector3f(neck).sub(waist)).add(waist).add(1.5f, 0, 0);
                        assertTrue(expectedNeck.distance(head.pivot()) < 1e-5f);
                        Quaternionf before = new Quaternionf().rotationZYX(roll, yaw, pitch);
                        Quaternionf after = new Quaternionf().rotationZYX(head.roll(), head.yaw(), head.pitch());
                        for (Vector3f axis : new Vector3f[]{new Vector3f(0, 0, -1), new Vector3f(0, -1, 0)})
                            assertTrue(before.transform(new Vector3f(axis)).distance(after.transform(new Vector3f(axis))) < 1e-5f);
                        assertEquals(new Vector3f(0, -2, 1), neck);
                    }
    }

    @Test void crouchedTorsoAttachmentStaysAtRealHipCentreThroughLargeYaw() {
        Vector3f body = new Vector3f(0, 5, 1), hips = new Vector3f(0, 10, -2);
        for (float pitch : new float[]{0.3f, 0.55f, 0.8f}) {
            Quaternionf original = new Quaternionf().rotationZYX(0.12f, 0.2f, pitch);
            Vector3f attachment = new Quaternionf(original).conjugate().transform(new Vector3f(hips).sub(body));
            for (float yaw : new float[]{-1.2f, -0.7f, 0.7f, 1.2f}) {
                Quaternionf turn = new Quaternionf().rotationZYX(0.05f, yaw, 0.08f);
                var carried = PelvisFollow.carry(body, pitch, 0.2f, 0.12f, turn, hips, 1.5f);
                Vector3f after = new Quaternionf().rotationZYX(carried.roll(), carried.yaw(), carried.pitch())
                        .transform(new Vector3f(attachment)).add(carried.pivot());
                assertTrue(after.distance(new Vector3f(hips).add(1.5f, 0, 0)) < 1e-4f,
                        "torso attachment must not orbit away from the animated hips");
            }
        }
    }

    @Test void carriedMeshUsesTheSameRotationAsItsPivot() {
        Vector3f pivot = new Vector3f(-2, 3, 1), waist = new Vector3f(0, 10, -2);
        Vector3f local = new Vector3f(1, 7, -2);
        Quaternionf original = new Quaternionf().rotationZYX(-0.1f, 0.3f, 0.6f);
        Quaternionf turn = new Quaternionf().rotationZYX(0.07f, 1.1f, -0.1f);
        Vector3f expected = turn.transform(original.transform(new Vector3f(local)).add(pivot).sub(waist))
                .add(waist).add(-1, 0, 0);
        var moved = PelvisFollow.carry(pivot, 0.6f, 0.3f, -0.1f, turn, waist, -1);
        Vector3f actual = new Quaternionf().rotationZYX(moved.roll(), moved.yaw(), moved.pitch())
                .transform(new Vector3f(local)).add(moved.pivot());
        assertTrue(actual.distance(expected) < 1e-4f);
    }

    @Test void crouchedWaistIsAtAnimatedBodyBottom() {
        Vector3f pivot = new Vector3f(0, 3, 2);
        Vector3f waist = PelvisFollow.waist(pivot, 0.55f, 0, 0, 12);
        assertEquals(3 + 12 * Math.cos(0.55), waist.y, 1e-5);
        assertEquals(2 + 12 * Math.sin(0.55), waist.z, 1e-5);
        assertEquals(new Vector3f(0, 3, 2), pivot);
    }

    @Test void followingHipsRetainsGroundedSolesAtAllStrideAngles() {
        for (float pitch = -2; pitch <= 2; pitch += 0.07f)
            for (float turn : new float[]{-0.42f, 0.42f})
                for (float shift : new float[]{-1.5f, 0, 1.5f}) {
                    Vector3f pivot = new Vector3f(1.9f, 12, 4);
                    Vector3f before = new Quaternionf().rotationZYX(0.1f, 0.2f, pitch)
                            .transform(new Vector3f(0, 12, 0)).add(pivot);
                    var leg = PelvisFollow.leg(pivot, pitch, 0.2f, 0.1f, 12,
                            new Vector3f(0, 12, 4), turn, shift);
                    Vector3f after = new Quaternionf().rotationZYX(leg.roll(), leg.yaw(), leg.pitch())
                            .transform(new Vector3f(0, 12, 0)).add(leg.pivot());
                    assertTrue(before.distance(after) < 0.001f, "sole must retain the IK contact");
                    assertTrue(Math.abs(leg.pivot().y - pivot.y) <= 0.7501f);
                }
    }

    @Test void neutralClearanceDoesNotChangePackLegPose() {
        Vector3f pivot = new Vector3f(2, 12, 4);
        var leg = PelvisFollow.leg(pivot, 0.5f, 0.2f, -0.1f, 12, new Vector3f(), 0, 0);
        assertEquals(pivot, leg.pivot());
        assertEquals(0.5f, leg.pitch());
        assertEquals(0.2f, leg.yaw());
        assertEquals(-0.1f, leg.roll());
    }

    @Test void hipFollowRemainsContinuousWhenReachLimitEngages() {
        Vector3f pivot = new Vector3f(1.9f, 12, 4);
        for (float pitch : new float[]{0.5f, 1.4f, 1.7f}) {
            Vector3f previous = new Vector3f(pivot);
            for (int i = 0; i <= 400; i++) {
                float weight = i / 400f;
                var leg = PelvisFollow.leg(pivot, pitch, 0, 0, 12,
                        new Vector3f(0, 12, 4), 0.42f * weight, 1.5f * weight);
                assertTrue(previous.distance(leg.pivot()) < 0.03f,
                        "reach limiting must not switch between discrete hip positions");
                previous = leg.pivot();
            }
        }
    }
    @Test void lowReachTranslationRetainsSolesAndLimitsHipHeight() {
        Vector3f pivot = new Vector3f(2, 12, 4);
        for (float pitch = -2; pitch <= 2; pitch += .07f)
            for (float dx : new float[]{-3.5f, 0, 3.5f})
                for (float dz : new float[]{-3.5f, 0, 3.5f}) {
                    Vector3f sole = PelvisFollow.waist(pivot, pitch, .2f, .1f, 12);
                    var leg = PelvisFollow.translate(pivot, pitch, .2f, .1f, 12, dx, dz);
                    assertTrue(sole.distance(PelvisFollow.waist(leg.pivot(), leg.pitch(), leg.yaw(), leg.roll(), 12)) < .001f);
                    assertTrue(Math.abs(leg.pivot().y - pivot.y) <= 2.0001f);
                }
    }

    @Test void translationReleaseIsContinuousAndNeutralPoseIsExact() {
        Vector3f pivot = new Vector3f(2, 12, 4);
        for (float pitch : new float[]{.5f, 1.4f, 1.7f}) {
            Vector3f previous = new Vector3f(pivot);
            for (int i = 0; i <= 4000; i++) {
                var leg = PelvisFollow.translate(pivot, pitch, .2f, .1f, 12, i * 3.5f / 4000, -i * 3.5f / 4000);
                assertTrue(previous.distance(leg.pivot()) < .02f, "pitch=" + pitch + " i=" + i + " jump=" + previous.distance(leg.pivot()));
                previous = leg.pivot();
            }
            var neutral = PelvisFollow.translate(pivot, pitch, .2f, .1f, 12, 0, 0);
            assertEquals(pivot, neutral.pivot());
            assertEquals(pitch, neutral.pitch());
            assertEquals(.2f, neutral.yaw());
            assertEquals(.1f, neutral.roll());
        }
    }
    @Test void setupStepReachesItsLiftedSoleWithoutStretchAndToeTwistDoesNotUnplantIt() {
        Vector3f hip = new Vector3f(2, 12, 0);
        for (float pitch : new float[]{-.1f, .1f, .4f})
            for (float side : new float[]{-1.5f, 1.5f})
                for (float lift : new float[]{0, -.4f, -.65f}) {
                    Vector3f offset = new Vector3f(side, lift, .8f);
                    Vector3f expected = PelvisFollow.waist(hip, pitch, 0, 0, 12).add(offset);
                    var leg = PelvisFollow.sole(hip, pitch, 0, 0, 12, offset, .07f);
                    Vector3f actual = PelvisFollow.waist(leg.pivot(), leg.pitch(), leg.yaw(), leg.roll(), 12);
                    assertTrue(actual.distance(expected) < 1e-4f);
                    assertEquals(12, actual.distance(leg.pivot()), 1e-4f);
                }
    }
    @Test void hiddenZeroLengthLegKeepsItsOriginalPose() {
        Vector3f hip = new Vector3f(2, 12, 0);
        var leg = PelvisFollow.sole(hip, .4f, .2f, .1f, 0, new Vector3f(1, -.5f, 1), .1f);
        assertEquals(hip, leg.pivot());
        assertEquals(.4f, leg.pitch());
        assertEquals(.2f, leg.yaw());
        assertEquals(.1f, leg.roll());
    }
}
