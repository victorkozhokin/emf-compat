package strm.emfcompat.animationadditions.blockuse;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CockpitContactTest {
    @Test void nearKeyCorrectionIsBoundedAndImprovesBothContactErrorsTogether() {
        var r = new Vector3f(-7, -11, 0);
        var l = new Vector3f(3, -11, 0);
        var rt = new Vector3f(-9, -11, 0);
        var lt = new Vector3f(8, -11, -10);
        var q = CockpitContact.fit(r, l, rt, lt);
        double before = Math.pow(r.distance(rt) - 11, 2) + Math.pow(l.distance(lt) - 11, 2);
        double after = Math.pow(q.transform(new Vector3f(r)).distance(rt) - 11, 2) + Math.pow(q.transform(new Vector3f(l)).distance(lt) - 11, 2);
        assertTrue(after < before * .5);
        assertTrue(q.angle() < Math.toRadians(31));
    }
    @Test void contactErrorDoesNotSnapTheTorsoDuringSmoothing() {
        var r = new Vector3f(-7, -11, 0);
        var l = new Vector3f(3, -11, 0);
        var rt = new Vector3f(-9, -11, 0);
        var lt = new Vector3f(8, -11, -10);
        var wanted = CockpitContact.fit(r, l, rt, lt);
        var old = new Quaternionf().rotationY((float) Math.toRadians(-25));
        var plain = new Quaternionf(old).slerp(wanted, .1f);
        assertTrue(CockpitContact.gap(plain, r, l, rt, lt) > 2.08f);
        var fixed = CockpitContact.follow(old, wanted, .1f, r, l, rt, lt);
        assertEquals(0, new Quaternionf(fixed).difference(plain).angle(), 1e-5);
        assertTrue(new Quaternionf(old).difference(fixed).angle() < new Quaternionf(old).difference(wanted).angle());
        var gradual = CockpitContact.follow(wanted, wanted, .1f, r, l, rt, lt);
        assertTrue(gradual.equals(wanted, 1e-6f)); // Component comparison avoids acos precision loss near zero.
    }
    @Test void exactContactsRequireNoBodyCorrection() {
        var r = new Vector3f(-5, -10, 0);
        var l = new Vector3f(5, -10, 0);
        var q = CockpitContact.fit(r, l, new Vector3f(r).add(0, 0, -11), new Vector3f(l).add(0, 0, -11));
        assertEquals(0, q.angle(), 1e-5);
    }
    @Test void lowThrottleLeansTowardEitherSideWithoutSacrificingTheWheel() {
        // Actual FA+Player shoulder and target samples from the seated mixed-control test.
        Vector3f[][] cases = {
            {new Vector3f(-4.170f, -9.492f, -2.009f), new Vector3f(5.623f, -8.979f, -.051f),
             new Vector3f(-8.529f, -12.179f, -10.940f), new Vector3f(17.070f, -7.953f, -11.040f)},
            {new Vector3f(-5.558f, -9.226f, -.445f), new Vector3f(4.331f, -9.744f, -1.838f),
             new Vector3f(-17.070f, -8.003f, -11.122f), new Vector3f(7.701f, -16.127f, -10.051f)}
        };
        for (var c : cases) {
            var q = CockpitContact.fit(c[0], c[1], c[2], c[3]);
            for (int hand = 0; hand < 2; hand++)
                assertTrue(Math.abs(q.transform(new Vector3f(c[hand])).distance(c[hand + 2]) - 11) < 2.08f);
            assertTrue(q.angle() < Math.toRadians(31));
        }
    }
    @Test void alternatingReachSolutionsRemainDampedAcrossRepeatedTurns() {
        var r = new Vector3f(-7, -11, 0);
        var l = new Vector3f(3, -11, 0);
        var rt = new Vector3f(-9, -11, 0);
        var lt = new Vector3f(8, -11, -10);
        var q = new Quaternionf();
        for (int i = 0; i < 180; i++) {
            var wanted = CockpitContact.fit(q, r, l, rt, lt);
            var next = CockpitContact.follow(q, wanted, .1f, r, l, rt, lt);
            float full = new Quaternionf(q).difference(wanted).angle();
            float step = new Quaternionf(q).difference(next).angle();
            assertTrue(step <= full * .11f + .001f, "Contact error must not bypass the smooth transition");
            q = next;
            rt.y += .015f * (float) Math.sin(i * .1);
            lt.y -= .015f * (float) Math.sin(i * .1);
        }
        assertTrue(CockpitContact.gap(q, r, l, rt, lt) < CockpitContact.gap(new Quaternionf(), r, l, rt, lt));
    }
    @Test void symmetricWheelContactDoesNotSwitchBetweenEquivalentLeanBranches() {
        var r = new Vector3f(-5, -10, 0);
        var l = new Vector3f(5, -10, 0);
        var previous = new Quaternionf();
        float maximumStep = 0;
        for (int i = 0; i < 720; i++) {
            float phase = (float) Math.toRadians(45 * Math.sin(i * Math.PI / 180));
            var rt = new Vector3f(-8 * (float) Math.cos(phase), -10 + 8 * (float) Math.sin(phase), -10);
            var lt = new Vector3f(8 * (float) Math.cos(phase), -10 - 8 * (float) Math.sin(phase), -10);
            var next = CockpitContact.fit(previous, r, l, rt, lt);
            if (i > 0) maximumStep = Math.max(maximumStep, new Quaternionf(previous).difference(next).angle());
            assertTrue(next.angle() < Math.toRadians(31));
            previous = next;
        }
        assertTrue(maximumStep < Math.toRadians(2), "Adjacent wheel contacts must keep a continuous torso solution");
    }
}
