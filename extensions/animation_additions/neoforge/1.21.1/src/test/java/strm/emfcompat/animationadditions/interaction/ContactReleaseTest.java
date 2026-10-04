package strm.emfcompat.animationadditions.interaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
class ContactReleaseTest {
    @Test void contactSurvivesTheOldTwoHundredMillisecondExpiry() {
        var r=new ContactRelease();r.start(.25);r.advance(.2);
        double t=.12/(6*.25);assertEquals(1-t*t*(3-2*t),r.remaining(),1e-6);
        assertEquals("release",r.phase());
    }
    @Test void bodyUnloadsBeforeTheHandWithdraws() {
        var r=new ContactRelease();r.start(.18);r.advance(.06);
        assertEquals(1,r.remaining());assertEquals("unload",r.phase());
        r.advance(.04);assertTrue(r.remaining()<1);
    }
    @Test void armourCopiesAndPauseDoNotAdvanceRelease() {
        var r=new ContactRelease();r.start(.2);r.advance(.3);float at=r.remaining();
        for(int i=0;i<100;i++)assertEquals(at,r.remaining());
        r.advance(0);assertEquals(at,r.remaining());
    }
    @ParameterizedTest @ValueSource(ints={30,60,144}) void samePoseAtEqualTimeAcrossFrameRates(int fps) {
        var r=new ContactRelease();r.start(.25);for(int i=0;i<fps;i++)r.advance(1.0/fps);
        double t=.92/(6*.25);assertEquals(1-t*t*(3-2*t),r.remaining(),1e-6);
    }
    @Test void forgottenContactDoesNotReturnOnReacquisition() {
        var r=new ContactRelease();r.start(.18);r.advance(2);assertEquals(0,r.remaining());
        r.cancel();r.start(.18);assertEquals(1,r.remaining());
    }
    @Test void withdrawalStartsAndEndsWithoutAnAngularVelocityKick() {
        var r=new ContactRelease();r.start(.2);r.advance(ContactRelease.UNLOAD_SECONDS);
        r.advance(.001);assertTrue((1-r.remaining())/.001<.01);
        r.advance(1.198);float beforeEnd=r.remaining();r.advance(.001);
        assertEquals(0,r.remaining());assertTrue(beforeEnd/.001<.01);
    }
    @Test void occupiedArmYieldsImmediately() {
        var r=new ContactRelease();r.start(.18);r.cancel();assertEquals(0,r.remaining());
    }
    @Test void reachKeepsAnExistingContactButDoesNotAcquireTooFarAway() {
        assertFalse(ContactReach.accepts(2.03f,2,false));assertTrue(ContactReach.accepts(2.03f,2,true));
        assertFalse(ContactReach.accepts(2.061f,2,true));assertFalse(ContactReach.accepts(Float.NaN,2,true));
    }
}
