package strm.touchnmotion.blockuse.supplementaries;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import strm.touchnmotion.blockuse.*;
class BellowsGeometryTest {
    @Test void plateFollowsNativeInterpolationWithoutOvershoot() {
        assertEquals(-.0625f, BellowsGeometry.height(0, -.125f, .5f));
        assertEquals(-.125f, BellowsGeometry.height(0, -.125f, 2));
        assertEquals(0, BellowsGeometry.height(0, -.125f, -1));
    }
    @Test void loadingAndReleaseUseActualCompression() {
        assertEquals(0, BellowsGeometry.compression(0));
        assertEquals(1, BellowsGeometry.compression(-.125f));
        assertEquals(.5f, BellowsGeometry.compression(-.0625f));
    }
    @Test void bothContactsTravelWithThePlate() {
        var top = new TableSurface(0, 1, 0, 1, 1, .1875);
        var compressed = new TableSurface(0, 1, 0, 1, .875, .1875);
        for (boolean right : new boolean[]{true, false}) {
            var a = top.contact(-.31, .5, right);
            var b = compressed.contact(-.31, .5, right);
            assertEquals(a.x(), b.x());
            assertEquals(a.z(), b.z());
            assertEquals(.125, a.y() - b.y());
        }
    }
}
