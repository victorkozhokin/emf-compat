package strm.touchnmotion.cape;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CapeFrameTest {
    @Test void aShiftAndTiltOfTheTorsoReachTheCapeTurnedAbout() {
        Matrix4f frame = CapeFollow.capeFrame(0, 0, 180);
        Matrix4f by = new Matrix4f().translation(.1f, 0, 0).rotateZ(-.14f);
        Matrix4f in = new Matrix4f(frame).invert().mul(by).mul(frame);
        Vector3f at = in.getTranslation(new Vector3f());
        Vector3f turn = in.getEulerAnglesZYX(new Vector3f());
        assertEquals(-.1f, at.x, 1e-5f);
        assertEquals(.14f, turn.z, 1e-5f);
    }
    @Test void whatTheCapeIsCarriedByLandsWhereTheTorsoWent() {
        Matrix4f frame = CapeFollow.capeFrame(17, 4, 176);
        Matrix4f by = new Matrix4f().translation(-.12f, .01f, .03f).rotateZ(-.14f).rotateX(.2f);
        Matrix4f cape = new Matrix4f().translation(0, -.05f, -.07f);
        Matrix4f carried = new Matrix4f(frame).mul(new Matrix4f(frame).invert().mul(by).mul(frame)).mul(cape);
        Matrix4f expected = new Matrix4f(by).mul(frame).mul(cape);
        assertTrue(carried.equals(expected, 1e-5f));
    }
}
