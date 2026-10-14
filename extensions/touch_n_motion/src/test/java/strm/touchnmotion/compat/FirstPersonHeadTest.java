package strm.touchnmotion.compat;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FirstPersonHeadTest {
    @Test void rotatedScaledTorsoKeepsTheVirtualNeckAtItsOriginalAnchor() {
        Matrix4f pack = new Matrix4f().translation(.08f, .16f, -.04f).rotateY(.4f).scale(.9f, 1.1f, 1.2f);
        Vector3f local = new Matrix4f(pack).invert().transformPosition(new Vector3f());
        for (float angle : new float[]{-.4f, 0, .4f}) {
            Matrix4f drawn = new Matrix4f().translation(.02f, -.1f, .13f).rotateZ(angle).scale(1.2f, .8f, 1);
            Vector3f neck = drawn.transformPosition(new Vector3f(local));
            Vector3f correction = FirstPersonHead.correction(pack, new Matrix4f(drawn), new Vector3f()).div(16);
            assertTrue(neck.add(correction).length() < .000001f);
            assertTrue(pack.equals(new Matrix4f().translation(.08f, .16f, -.04f).rotateY(.4f).scale(.9f, 1.1f, 1.2f)));
        }
    }
    @Test void thePacksSeatedNeckHeightIsRetained() {
        Matrix4f pack = new Matrix4f().translation(0, .3f, 0);
        Vector3f by = FirstPersonHead.correction(pack, new Matrix4f(pack).translate(.1f, .2f, 0));
        assertEquals(-1.6f, by.x, .00001f);
        assertEquals(-3.2f, by.y, .00001f);
    }
    @Test void unchangedTorsoNeedsNoCorrection() {
        Matrix4f pose = new Matrix4f().translation(.1f, .2f, .3f).rotateX(.3f);
        assertTrue(FirstPersonHead.correction(pose, new Matrix4f(pose)).length() < .00001f);
    }
}
