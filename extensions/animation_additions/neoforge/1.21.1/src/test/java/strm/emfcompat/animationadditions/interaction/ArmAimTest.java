package strm.emfcompat.animationadditions.interaction;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ArmAimTest {

    /** The renderer turns a part by ZYX; an arm hanging along +y turned by the angles points at the target. */
    @Test
    void theAnglesPointTheHangingArmAtTheTarget() {
        float[][] targets = {{0, 0, -8}, {-5, 3, -6}, {4, -7, -2}, {6, 9, 1}, {0, 11, 0}, {-3, -10, -0.5f}, {2, 1, 9}};
        for (float[] t : targets) {
            float[] aim = ArmAim.angles(t[0], t[1], t[2]);
            Vector3f arm = new Quaternionf().rotationZYX(0f, aim[1], aim[0]).transform(new Vector3f(0, 1, 0));
            Vector3f want = new Vector3f(t).normalize();
            assertEquals(0f, arm.distance(want), 1e-4f, "towards " + want);
        }
    }

    @Test
    void noWayAtAllIsNoAim() {
        assertNull(ArmAim.angles(0, 0, 0));
        assertNull(ArmAim.angles(1e-4f, 0, -1e-4f));
    }

    @Test
    void anyLengthOfTheSameWayGivesTheSameAngles() {
        float[] near = ArmAim.angles(-1, 2, -3), far = ArmAim.angles(-10, 20, -30);
        assertEquals(near[0], far[0], 1e-5f);
        assertEquals(near[1], far[1], 1e-5f);
    }
}
