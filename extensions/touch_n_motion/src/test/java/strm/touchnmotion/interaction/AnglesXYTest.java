package strm.touchnmotion.interaction;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import strm.emfcompat.core.ik.IKMath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Core's IKMath.anglesXY, as Touch'n Motion aims arms with it. */
class AnglesXYTest {

    /** The renderer turns a part by ZYX; an arm hanging along +y turned by the angles points at the target. */
    @Test
    void theAnglesPointTheHangingArmAtTheTarget() {
        float[][] targets = {{0, 0, -8}, {-5, 3, -6}, {4, -7, -2}, {6, 9, 1}, {0, 11, 0}, {-3, -10, -0.5f}, {2, 1, 9}};
        for (float[] t : targets) {
            IKMath.Angles aim = IKMath.anglesXY(t[0], t[1], t[2]);
            Vector3f arm = new Quaternionf().rotationZYX(0f, aim.secondary(), aim.x()).transform(new Vector3f(0, 1, 0));
            Vector3f want = new Vector3f(t).normalize();
            assertEquals(0f, arm.distance(want), 1e-4f, "towards " + want);
        }
    }

    @Test
    void noWayAtAllIsNoAim() {
        assertNull(IKMath.anglesXY(0, 0, 0));
        assertNull(IKMath.anglesXY(1e-4f, 0, -1e-4f));
        assertNull(IKMath.anglesXY(Float.NaN, 1, 0));
    }

    @Test
    void anyLengthOfTheSameWayGivesTheSameAngles() {
        IKMath.Angles near = IKMath.anglesXY(-1, 2, -3), far = IKMath.anglesXY(-10, 20, -30);
        assertEquals(near.x(), far.x(), 1e-5f);
        assertEquals(near.secondary(), far.secondary(), 1e-5f);
    }

    /** The exact inverse of rotationZYX, also for a yawed and rolled limb. */
    @Test
    void eulerZYXInvertsRotationZYX() {
        float[][] angles = {{0.3f, -0.8f, 0.2f}, {-1.2f, 0.9f, -0.4f}, {0.05f, 1.4f, 0.7f}};
        for (float[] a : angles) {
            Vector3f back = IKMath.eulerZYX(new Quaternionf().rotationZYX(a[2], a[1], a[0]));
            assertEquals(a[0], back.x, 1e-4f);
            assertEquals(a[1], back.y, 1e-4f);
            assertEquals(a[2], back.z, 1e-4f);
        }
    }
}
