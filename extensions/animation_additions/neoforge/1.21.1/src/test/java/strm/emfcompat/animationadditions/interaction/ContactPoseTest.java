package strm.emfcompat.animationadditions.interaction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ContactPoseTest {
    @Test void handoffKeepsWindingRollAndPivotOffsets() {
        float[] last = {-2.5f, .7f, -.5f, 1, 2, 3}, next = {0, 0, 0, 0, 0, 0};
        assertArrayEquals(last, ContactPose.follow(last, next, 0));
        assertEquals(-.25f, ContactPose.follow(last, next, .5f)[2]);
        assertEquals(1.5f, ContactPose.follow(last, next, .5f)[5]);
        assertArrayEquals(new float[]{-2.5f, .7f, -.5f, 1, 2, 3}, last);
    }
    @Test void oppositeSidesOfPiTakeTheShortPath() {
        float a = (float) Math.toRadians(179), b = (float) Math.toRadians(-179);
        var result = ContactPose.follow(new float[]{a, 0, 0, 0, 0, 0}, new float[]{b, 0, 0, 0, 0, 0}, .5f);
        assertEquals(Math.PI, result[0], 1e-6);
    }
}
