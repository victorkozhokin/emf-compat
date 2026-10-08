package strm.touchnmotion.blockuse;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HeadClearanceTest {
    final Vector3f blockMin = new Vector3f(-8, -16, -16), blockMax = new Vector3f(8, 0, 0);
    @Test void fullHeadVolumeIsKeptOutsideTheCasing() {
        var min = new Vector3f(-4, -8, -3);
        var max = new Vector3f(4, 0, 5);
        float shift = HeadClearance.shift(min, max, blockMin, blockMax);
        assertEquals(3.5f, shift);
        min.z += shift;
        max.z += shift;
        assertEquals(0, HeadClearance.overlap(min, max, blockMin, blockMax));
    }
    @Test void clothingMarginStartsContinuouslyBeforeContact() {
        assertEquals(0, HeadClearance.shift(new Vector3f(-4, -8, .51f), new Vector3f(4, 0, 8.51f), blockMin, blockMax));
        assertEquals(.01f, HeadClearance.shift(new Vector3f(-4, -8, .49f), new Vector3f(4, 0, 8.49f), blockMin, blockMax), 1e-5);
    }
    @Test void HeadAboveTheBlockNeedsNoBackoff() {
        assertEquals(0, HeadClearance.shift(new Vector3f(-4, -25, -3), new Vector3f(4, -17, 5), blockMin, blockMax));
    }
}
