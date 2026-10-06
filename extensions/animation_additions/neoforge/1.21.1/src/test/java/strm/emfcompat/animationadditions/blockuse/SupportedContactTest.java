package strm.emfcompat.animationadditions.blockuse;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupportedContactTest {
    @Test void discontinuousTargetNeverBypassesTheAngularSpeedLimit() {
        var previous = new Quaternionf().rotationY((float) Math.toRadians(-25));
        var wanted = new Quaternionf().rotationX((float) Math.toRadians(30));
        var result = SupportedContact.follow(previous, wanted, .05);
        assertTrue(distance(previous, result) <= 3.01);
        assertEquals(previous, SupportedContact.follow(previous, wanted, 0));
    }
    @Test void breathingAndRepeatedCompressionKeepTheSameSolutionBranch() {
        var previous = new Quaternionf();
        var r = new Vector3f(-5, -11, 0);
        var l = new Vector3f(5, -11, 0);
        for (int frame = 0; frame < 400; frame++) {
            float breath = (float) Math.sin(frame * .06) * .08f, compression = (float) Math.sin(frame * .12) * .5f;
            Vector3f rt = new Vector3f(-3, -7 + compression, -12), lt = new Vector3f(3, -7 + compression, -12);
            var wanted = SupportedContact.fit(previous, new Vector3f(r).add(0, breath, 0), new Vector3f(l).add(0, breath, 0), rt, lt);
            var next = SupportedContact.follow(previous, wanted, .05);
            assertTrue(distance(previous, next) < 3.01);
            assertTrue(next.angle() <= Math.toRadians(30.01));
            if (frame > 60) assertTrue(CockpitContact.gap(next, r, l, rt, lt) < 2.08);
            previous.set(next);
        }
    }
    private static double distance(Quaternionf a, Quaternionf b) {
        return Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(a.dot(b)))));
    }
}
