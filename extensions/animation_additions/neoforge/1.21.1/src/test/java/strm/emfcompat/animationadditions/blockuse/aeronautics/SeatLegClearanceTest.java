package strm.emfcompat.animationadditions.blockuse.aeronautics;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import strm.emfcompat.animationadditions.blockuse.*;
class SeatLegClearanceTest {
    private static float penetration(float top, float hip, float pitch, float yaw) {
        var q = new Quaternionf().rotationZYX(0, yaw, pitch);
        var centre = q.transform(new Vector3f(0, -.375f, 0)).add(.5f, hip, .5f);
        var axes = new Vector3f[]{new Vector3f(.140625f, 0, 0), new Vector3f(0, .390625f, 0), new Vector3f(0, 0, .140625f)};
        for (var a : axes) q.transform(a);
        return SeatLegClearance.penetration(centre, axes, new Vector3f(0, 0, 0), new Vector3f(1, top, 1));
    }
    @Test void oldFloorContactCanBuryTheLegInBothSeats() {
        assertTrue(penetration(.375f, .5f, -.862f, .12f) > .02f);
        assertTrue(penetration(1, .6875f, -.5f, .12f) > .02f);
    }
    @Test void HorizontalThighClearsCushionsOfDifferentHeightsAndBothHandedSides() {
        for (float top : new float[]{.375f, .5625f, 1}) for (float yaw : new float[]{-.35f, 0, .35f})
            assertEquals(0, penetration(top, top + .140625f, -(float) Math.PI / 2, yaw), 1e-5);
    }
    @Test void TangentAndSeparatedCuboidsAreNotPenetrations() {
        Vector3f[] half = {new Vector3f(.5f, 0, 0), new Vector3f(0, .5f, 0), new Vector3f(0, 0, .5f)};
        assertEquals(0, SeatLegClearance.penetration(new Vector3f(1.5f, .5f, .5f), half, new Vector3f(), new Vector3f(1)), 1e-6);
        assertEquals(.25f, SeatLegClearance.penetration(new Vector3f(1.25f, .5f, .5f), half, new Vector3f(), new Vector3f(1)), 1e-6);
    }
}
