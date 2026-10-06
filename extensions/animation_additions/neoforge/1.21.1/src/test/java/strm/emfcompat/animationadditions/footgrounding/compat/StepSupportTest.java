package strm.emfcompat.animationadditions.footgrounding.compat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StepSupportTest {
    @Test void trailingFootDoesNotSinkBodyAfterClimbing() {
        // In-game regression: player at Y=151, old plant at 150,
        // both hips on the upper surface, stride requested 8.33px lowering.
        assertEquals(1.52f, StepSupport.bodyDrop(8.33f, .02f, .02f, 151, 150, 151.5, false), .0001);
    }
    @Test void partialStairContactAndFenceOutlineAreRetained() {
        assertEquals(7.5f, StepSupport.bodyDrop(9, 6, 6, 150.5, 150, 150.5, false));
        assertEquals(10.67f, StepSupport.bodyDrop(10.67f, 10.67f, 10.67f, 151.5, 151, 151, false));
    }
    @Test void DescendingAndOrdinaryFlatStrideAreUnchanged() {
        assertEquals(8, StepSupport.bodyDrop(8, 0, 0, 151, 150, 151, true));
        assertEquals(4, StepSupport.bodyDrop(4, 0, 0, 150, 150, 150, false));
        assertEquals(4, StepSupport.bodyDrop(4, 0, 0, 150, Double.NaN, 150, false));
    }
}
