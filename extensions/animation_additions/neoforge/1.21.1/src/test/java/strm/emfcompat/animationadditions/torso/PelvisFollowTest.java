package strm.emfcompat.animationadditions.torso;

import org.junit.jupiter.api.Test;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class PelvisFollowTest {
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
        assertEquals(0.5f, leg.pitch()); assertEquals(0.2f, leg.yaw()); assertEquals(-0.1f, leg.roll());
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
}
