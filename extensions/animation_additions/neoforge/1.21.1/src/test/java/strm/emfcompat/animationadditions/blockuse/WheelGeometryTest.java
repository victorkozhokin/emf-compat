package strm.emfcompat.animationadditions.blockuse;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WheelGeometryTest {
    @Test void steeringRollCarriesUpperTorsoTowardLowerHandAndStaysSmall() {
        assertEquals(0, WheelGeometry.steeringRoll(2, 2), 1e-6);
        for (float difference : new float[]{-32, -16, -8, 8, 16, 32}) {
            float roll = WheelGeometry.steeringRoll(difference, 0);
            Vector3f upperTorso = new Vector3f(0, -12, 0).rotateZ(roll);
            assertTrue(upperTorso.x * difference < 0, "Lower right hand must carry torso toward -X");
            assertTrue(Math.abs(roll) <= Math.toRadians(5) + 1e-6);
            assertEquals(-roll, WheelGeometry.steeringRoll(0, difference), 1e-6);
        }
    }
    @Test void valveMatchesRendererMatrixOnAllSixFacesAndReverseTurns() {
        for (Vector3f face : new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(-1, 0, 0),
                new Vector3f(0, 1, 0), new Vector3f(0, -1, 0), new Vector3f(0, 0, 1), new Vector3f(0, 0, -1)}) {
            for (float angle : new float[]{-6.2f, -1.7f, 0, .4f, 3.1f, 6.2f}) {
                Vector3f model = new Vector3f(.125f, .40625f, .5f);
                Vector3f expected = new Matrix4f().translate(.5f, .5f, .5f)
                        .rotate(angle, Math.abs(face.x), Math.abs(face.y), Math.abs(face.z))
                        .rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), face))
                        .translate(-.5f, -.5f, -.5f).transformPosition(new Vector3f(model));
                assertEquals(0, expected.distance(WheelGeometry.valve(model, face, angle)), 1e-6);
            }
        }
    }
    @Test void steeringUsesRadiansAndMountTranslationInsideFacingRotation() {
        for (boolean floor : new boolean[]{true, false}) {
            for (float yaw : new float[]{0, 1.57f, 3.14f, 4.71f}) {
                Quaternionf facing = new Quaternionf().rotationYXZ(yaw, 1.5707964f, 0);
                for (float angle : new float[]{-3.14f, -.785f, 0, .785f, 3.14f}) {
                    Vector3f model = new Vector3f(1, .5f, .5f);
                    Vector3f expected = new Matrix4f().translate(.5f, .5f, .5f).rotate(facing)
                            .translate(0, .40625f, floor ? -.3125f : .3125f).rotateY(angle)
                            .translate(-.5f, -.5f, -.5f).transformPosition(new Vector3f(model));
                    assertEquals(0, expected.distance(WheelGeometry.steering(model, facing, floor, angle)), 1e-6);
                }
            }
        }
    }
    @Test void steeringHandsKeepTheirSeparationAndReturnAfterAFullTurn() {
        Quaternionf facing = new Quaternionf().rotationZ(1.5707964f);
        for (float angle = -6.3f; angle < 6.3f; angle += .05f) {
            Vector3f right = WheelGeometry.steering(new Vector3f(1, .5f, .5f), facing, true, angle);
            Vector3f left = WheelGeometry.steering(new Vector3f(0, .5f, .5f), facing, true, angle);
            assertEquals(1, right.distance(left), 1e-6);
            assertEquals(0, right.distance(WheelGeometry.steering(new Vector3f(1, .5f, .5f), facing, true,
                    angle + (float)(2 * Math.PI))), 1e-6);
        }
    }
}
