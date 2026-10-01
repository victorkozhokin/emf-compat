package strm.emfcompat.animationadditions.footgrounding.compat;

import org.junit.jupiter.api.Test;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BalanceMathTest {
    @Test void straightLegIkKeepsSoleHeightThroughoutTheStride() {
        for (float pitch = -0.7f; pitch <= 0.7f; pitch += 0.1f)
            for (float yaw : new float[]{-0.2f, 0, 0.2f})
                for (float dx : new float[]{-1.9f, 1.9f}) {
                    Vector3f before = new Quaternionf().rotationZYX(0.1f, yaw, pitch)
                            .transform(new Vector3f(0, 12, 0));
                    var result = BalanceMath.leg(pitch, yaw, 0.1f, 12, dx, 0.2f);
                    Vector3f after = new Quaternionf().rotationZYX(result.roll(), result.yaw(), result.pitch())
                            .transform(new Vector3f(0, 12, 0));
                    assertEquals(before.x + dx, after.x, 1e-4);
                    assertEquals(before.z + 0.2f, after.z, 1e-4);
                    assertEquals(before.y, after.y + result.pivotY(), 1e-4);
                }
    }

    @Test void slopeLeanHasCorrectSignInBothAxesAndStaysBounded() {
        assertArrayEquals(new float[]{0, 0}, BalanceMath.slope(0, -1, 0), 1e-6f);
        assertTrue(BalanceMath.slope(0.1f, -1, 0)[1] > 0);
        assertTrue(BalanceMath.slope(-0.1f, -1, 0)[1] < 0);
        assertTrue(BalanceMath.slope(0, -1, 0.1f)[0] < 0);
        assertTrue(BalanceMath.slope(0, -1, -0.1f)[0] > 0);
        for (float v : BalanceMath.slope(100, -1, -100))
            assertTrue(Math.abs(v) <= Math.toRadians(8) + 1e-6);
    }

    @Test void impossibleReachDoesNotProduceNaNOrOverstretch() {
        var result = BalanceMath.leg(0, 0, 0, 12, 100, 100);
        Vector3f sole = new Quaternionf().rotationZYX(result.roll(), result.yaw(), result.pitch())
                .transform(new Vector3f(0, 12, 0));
        assertEquals(12, sole.length(), 1e-4);
        assertTrue(Float.isFinite(result.pivotY()));
    }

    @Test void hiddenOrInvertedLegScaleDoesNotProduceNaN() {
        for (float length : new float[]{0, -12})
            assertEquals(new BalanceMath.Leg(0.2f, 0.3f, 0.1f, 0),
                    BalanceMath.leg(0.2f, 0.3f, 0.1f, length, 2, 2));
    }

    @Test void voxelRampLeanFollowsHeightsAndCancelsOnFlatFloor() {
        for (float gradient : new float[]{-0.1f, 0, 0.1f}) {
            List<BalanceMath.Sample> samples = new ArrayList<>();
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
                samples.add(new BalanceMath.Sample(x, z, 10 + gradient * z));
            float[] lean = BalanceMath.surfaceSlope(samples);
            assertEquals(-Math.atan(gradient), lean[0], 1e-5);
            assertEquals(0, lean[1], 1e-5);
        }
    }

    @Test void insufficientOrCollinearContactsNeverInventASlope() {
        assertArrayEquals(new float[2], BalanceMath.surfaceSlope(List.of()));
        assertArrayEquals(new float[2], BalanceMath.surfaceSlope(List.of(
                new BalanceMath.Sample(0,0,0), new BalanceMath.Sample(0,1,2),
                new BalanceMath.Sample(0,2,4))));
    }
}
