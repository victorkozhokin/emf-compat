package strm.emfcompat.animationadditions.blockuse;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CockpitContactTest {
    @Test void nearKeyCorrectionIsBoundedAndImprovesBothContactErrorsTogether() {
        var r=new Vector3f(-7,-11,0);var l=new Vector3f(3,-11,0);
        var rt=new Vector3f(-9,-11,0);var lt=new Vector3f(8,-11,-10);
        var q=CockpitContact.fit(r,l,rt,lt);
        double before=Math.pow(r.distance(rt)-11,2)+Math.pow(l.distance(lt)-11,2);
        double after=Math.pow(q.transform(new Vector3f(r)).distance(rt)-11,2)+Math.pow(q.transform(new Vector3f(l)).distance(lt)-11,2);
        assertTrue(after<before*.5);assertTrue(q.angle()<Math.toRadians(31));
    }
    @Test void sourcePoseDiscontinuityDoesNotDetachTheHandsDuringSmoothing() {
        var r=new Vector3f(-7,-11,0);var l=new Vector3f(3,-11,0);
        var rt=new Vector3f(-9,-11,0);var lt=new Vector3f(8,-11,-10);
        var wanted=CockpitContact.fit(r,l,rt,lt);
        var old=new Quaternionf().rotationY((float)Math.toRadians(-25));
        var plain=new Quaternionf(old).slerp(wanted,.1f);
        assertTrue(CockpitContact.gap(plain,r,l,rt,lt)>2.08f);
        var fixed=CockpitContact.follow(old,wanted,.1f,r,l,rt,lt);
        assertEquals(CockpitContact.gap(wanted,r,l,rt,lt),CockpitContact.gap(fixed,r,l,rt,lt),1e-5);
        var gradual=CockpitContact.follow(wanted,wanted,.1f,r,l,rt,lt);
        assertEquals(0,new Quaternionf(gradual).difference(wanted).angle(),1e-5);
    }
    @Test void exactContactsRequireNoBodyCorrection() {
        var r=new Vector3f(-5,-10,0);var l=new Vector3f(5,-10,0);
        var q=CockpitContact.fit(r,l,new Vector3f(r).add(0,0,-11),new Vector3f(l).add(0,0,-11));
        assertEquals(0,q.angle(),1e-5);
    }
    @Test void lowThrottleLeansTowardEitherSideWithoutSacrificingTheWheel() {
        // Actual FA+Player shoulder and target samples from the seated mixed-control test.
        Vector3f[][] cases={
            {new Vector3f(-4.170f,-9.492f,-2.009f),new Vector3f(5.623f,-8.979f,-.051f),
             new Vector3f(-8.529f,-12.179f,-10.940f),new Vector3f(17.070f,-7.953f,-11.040f)},
            {new Vector3f(-5.558f,-9.226f,-.445f),new Vector3f(4.331f,-9.744f,-1.838f),
             new Vector3f(-17.070f,-8.003f,-11.122f),new Vector3f(7.701f,-16.127f,-10.051f)}
        };
        for(var c:cases) {
            var q=CockpitContact.fit(c[0],c[1],c[2],c[3]);
            for(int hand=0;hand<2;hand++)
                assertTrue(Math.abs(q.transform(new Vector3f(c[hand])).distance(c[hand+2])-11)<2.08f);
            assertTrue(q.angle()<Math.toRadians(31));
        }
    }
}
