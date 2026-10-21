package strm.touchnmotion.pose;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyPoseTest {
    @Test void mirroringSwapsTheSidesAndTurnsTheOtherWay() {
        BodyPose pose = new BodyPose();
        pose.reset();
        pose.right = new float[]{-1f, .3f, .2f, 1.5f};
        pose.rightFoot = new Vector3f(-1f, 0, 1.2f);
        pose.leftFoot = new Vector3f(.4f, 0, -.8f);
        pose.head = new float[]{.2f, .4f};
        pose.yaw = .3f;
        pose.roll = .1f;
        pose.pitch = .15f;
        pose.mirror();
        assertNull(pose.right);
        assertArrayEquals(new float[]{-1f, -.3f, -.2f, 1.5f}, pose.left, 1e-6f);
        assertEquals(new Vector3f(1f, 0, 1.2f), pose.leftFoot);
        assertEquals(new Vector3f(-.4f, 0, -.8f), pose.rightFoot);
        assertArrayEquals(new float[]{.2f, -.4f}, pose.head, 1e-6f);
        assertEquals(-.3f, pose.yaw, 1e-6f);
        assertEquals(-.1f, pose.roll, 1e-6f);
        assertEquals(.15f, pose.pitch, 1e-6f);
        pose.mirror();
        assertArrayEquals(new float[]{-1f, .3f, .2f, 1.5f}, pose.right, 1e-6f);
    }
}
