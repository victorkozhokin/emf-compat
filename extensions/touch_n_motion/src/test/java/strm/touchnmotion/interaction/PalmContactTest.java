package strm.touchnmotion.interaction;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PalmContactTest {
    @Test void fullContactPreservesLengthAndPointsAtMovingTargetFromAnyBasePose() {
        float[] base = {.6f, -.8f, .4f};
        for (Vector3f target : new Vector3f[]{new Vector3f(-7, -4, -8), new Vector3f(6, 2, -9), new Vector3f(0, 11, 0)}) {
            float[] angles = ContactAim.palmRotation(base, target.x, target.y, target.z, 1);
            Vector3f palm = new Quaternionf().rotationZYX(angles[2], angles[1], angles[0]).transform(new Vector3f(0, 11, 0));
            assertEquals(11, palm.length(), 1e-5);
            assertEquals(1, palm.normalize().dot(new Vector3f(target).normalize()), 1e-5);
        }
        assertArrayEquals(new float[]{.6f, -.8f, .4f}, base);
    }

    @Test void releaseAndInvalidTargetsLeaveThePackPoseAlone() {
        float[] base = {.3f, -.2f, .1f};
        assertArrayEquals(base, ContactAim.palmRotation(base, -6, -3, -7, 0), 1e-5f);
        assertArrayEquals(base, ContactAim.palmRotation(base, 0, 0, 0, 1));
        assertArrayEquals(base, ContactAim.palmRotation(base, Float.NaN, 1, 0, 1));
    }

}
