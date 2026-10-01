package strm.emfcompat.animationadditions.footgrounding.compat;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SupportSurfaceTest {
    @Test void expandedScanKeepsBothFenceEdgesAtHitboxOverhang() {
        for (int sign : new int[]{-1, 1}) {
            List<SupportSurface.Point> small = new ArrayList<>(), expanded = new ArrayList<>();
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                // Player centre is 0.41 blocks off a quarter-block fence strip.
                if (Math.abs(x * 1.5f + sign * 7f) > 2) continue;
                var point = new SupportSurface.Point(x * 1.5f, z * 1.5f);
                expanded.add(point);
                if (Math.abs(x) <= 4 && Math.abs(z) <= 4) small.add(point);
            }
            assertEquals(0, new SupportSurface(small, 1.5f).narrow,
                    "original scan loses the second edge");
            var fence = new SupportSurface(expanded, 1.5f, 12);
            assertTrue(fence.narrow > 0.7f);
            var foot = fence.place(0, 0);
            assertEquals(-sign * 6, foot.x(), 1e-4, "bounded correction towards actual support");
        }
    }

    @Test void expandedScanStillRejectsBroadFloorEdgeAndMissingSupport() {
        List<SupportSurface.Point> edge = new ArrayList<>();
        for (int x = -8; x <= -4; x++) for (int z = -8; z <= 8; z++)
            edge.add(new SupportSurface.Point(x * 1.5f, z * 1.5f));
        assertEquals(0, new SupportSurface(edge, 1.5f, 12).narrow);
        assertEquals(0, new SupportSurface(List.of(), 1.5f, 12).narrow);
    }

    @Test void idleFeetAreSeparatedAndStrideIsReleasedAtEveryHeading() {
        for (int degrees = 0; degrees < 180; degrees += 5) {
            var beam = strip(Math.toRadians(degrees));
            var r = beam.stance(-1.9f, 0, -1, 1);
            var l = beam.stance(1.9f, 0, 1, 1);
            assertEquals(5, Math.hypot(r.x() - l.x(), r.z() - l.z()), 1e-4,
                    "separate idle soles along the supported direction, heading=" + degrees);
            assertEquals(beam.place(-1.9f, 3), beam.stance(-1.9f, 3, -1, 0));
        }
    }

    @Test void stoneWallWithTwoEdgesIsNarrowButAFullBlockEdgeIsNot() {
        List<SupportSurface.Point> points = new ArrayList<>();
        for (int x = -2; x <= 2; x++) for (int z = -4; z <= 4; z++)
            points.add(new SupportSurface.Point(x * 1.5f, z * 1.5f));
        var wall = new SupportSurface(points, 1.5f);
        assertTrue(wall.narrow > 0);
        assertTrue(wall.place(1.9f, 0).x() < 1.9f);
    }
    private static SupportSurface strip(double angle) {
        List<SupportSurface.Point> points = new ArrayList<>();
        for (int i = -4; i <= 4; i++) {
            points.add(new SupportSurface.Point((float)(i * 1.5 * Math.cos(angle)),
                    (float)(i * 1.5 * Math.sin(angle))));
        }
        return new SupportSurface(points, 1.5f);
    }

    @Test void feetLandOnFenceInsteadOfEitherSide() {
        SupportSurface fence = strip(Math.PI / 2);
        for (float x : new float[]{-1.9f, 1.9f}) {
            var foot = fence.place(x, 2.2f);
            assertEquals(0, foot.x(), 1e-5);
            assertEquals(2.2f, foot.z(), 1e-5, "preserve the along-fence stride");
        }
    }

    @Test void diagonalSupportWorksAtEveryBodyHeading() {
        for (int degrees = 0; degrees < 180; degrees += 5) {
            double a = Math.toRadians(degrees);
            var foot = strip(a).place((float)(2 * Math.cos(a) - 2 * Math.sin(a)),
                    (float)(2 * Math.sin(a) + 2 * Math.cos(a)));
            assertEquals(0, foot.x() * -Math.sin(a) + foot.z() * Math.cos(a), 1e-5,
                    "sole must lie on the strip, angle=" + degrees);
            assertEquals(2, foot.x() * Math.cos(a) + foot.z() * Math.sin(a), 1e-5);
        }
    }

    @Test void fullFloorAndOrdinaryBlockEdgeKeepOriginalStance() {
        for (int start : new int[]{-4, 0}) {
            List<SupportSurface.Point> points = new ArrayList<>();
            for (int x = start; x <= 4; x++) for (int z = -4; z <= 4; z++)
                points.add(new SupportSurface.Point(x * 1.5f, z * 1.5f));
            SupportSurface floor = new SupportSurface(points, 1.5f);
            assertEquals(0, floor.narrow);
            assertEquals(new SupportSurface.Placement(-1.9f, 3), floor.place(-1.9f, 3));
        }
    }

    @Test void missingFloorNeverCreatesAnImaginaryContact() {
        SupportSurface air = new SupportSurface(List.of(), 1.5f);
        assertEquals(0, air.narrow);
        assertEquals(new SupportSurface.Placement(2, 4), air.place(2, 4));
    }

    @Test void endOfBeamAndIsolatedPostUseActualSurface() {
        var foot = strip(Math.PI / 2).place(2, 7);
        assertEquals(0, foot.x(), 1e-5);
        assertEquals(6, foot.z(), 1e-5);
        var post = new SupportSurface(List.of(new SupportSurface.Point(0, 0)), 1.5f).place(2, 2);
        assertEquals(0, post.x()); assertEquals(0, post.z());
    }

    @Test void correctionsAreBoundedAndFinite() {
        var beam = strip(0);
        var foot = beam.place(0, 100);
        assertEquals(6, Math.hypot(foot.x(), foot.z() - 100), 1e-5);
        assertTrue(Float.isFinite(foot.x()) && Float.isFinite(foot.z()));
    }
}
