package strm.touchnmotion.interaction;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartMathTest {

    /** The game: translate by the pivot in sixteenths, turn by a ZYX quaternion, scale - in that order on the stack. */
    private static Matrix4f asTheGameDoes(float x, float y, float z, float xRot, float yRot, float zRot, float scale) {
        return new Matrix4f().translate(x / 16f, y / 16f, z / 16f)
                .rotate(new Quaternionf().rotationZYX(zRot, yRot, xRot)).scale(scale, scale, scale);
    }

    @Test
    void aPartsTransformIsTheGamesOwn() {
        Matrix4f ours = PartMath.transform(1.5f, 0.6f, -2f, 0.08f, 0.23f, -0.05f, 1.1f, 1.1f, 1.1f);
        Matrix4f theirs = asTheGameDoes(1.5f, 0.6f, -2f, 0.08f, 0.23f, -0.05f, 1.1f);
        for (Vector3f point : new Vector3f[]{new Vector3f(0, 0, 0), new Vector3f(-5 / 16f, 2 / 16f, 0), new Vector3f(0.3f, 1.2f, -0.4f)}) {
            Vector3f a = ours.transformPosition(new Vector3f(point)), b = theirs.transformPosition(new Vector3f(point));
            assertEquals(b.x, a.x, 1e-6);
            assertEquals(b.y, a.y, 1e-6);
            assertEquals(b.z, a.z, 1e-6);
        }
    }

    @Test
    void aRootAtRestChangesNothing() {
        Vector3f shoulder = new Vector3f(-5 / 16f, 2 / 16f, 0);
        Vector3f moved = PartMath.transform(0, 0, 0, 0, 0, 0, 1, 1, 1).transformPosition(new Vector3f(shoulder));
        assertEquals(0f, moved.distance(shoulder), 1e-7);
    }

    /** What is at stake: a root turned as Fresh Animations turns it, walking aslant, carries a hand at arm's length this far. */
    @Test
    void aTurnedRootMovesAHandByPixels() {
        Vector3f hand = new Vector3f(-5 / 16f, 2 / 16f, -10 / 16f);
        Vector3f moved = PartMath.transform(0, 0, 0, 0, 0.23f, 0.08f, 1, 1, 1).transformPosition(new Vector3f(hand));
        float pixels = moved.distance(hand) * 16f;
        assertEquals(2.6f, pixels, 0.6f);
    }
}
