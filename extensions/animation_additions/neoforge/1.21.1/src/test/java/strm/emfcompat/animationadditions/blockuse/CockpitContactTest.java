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
        assertTrue(after<before*.5);assertTrue(q.angle()<Math.toRadians(26));
    }
    @Test void exactContactsRequireNoBodyCorrection() {
        var r=new Vector3f(-5,-10,0);var l=new Vector3f(5,-10,0);
        var q=CockpitContact.fit(r,l,new Vector3f(r).add(0,0,-11),new Vector3f(l).add(0,0,-11));
        assertEquals(0,q.angle(),1e-5);
    }
}
