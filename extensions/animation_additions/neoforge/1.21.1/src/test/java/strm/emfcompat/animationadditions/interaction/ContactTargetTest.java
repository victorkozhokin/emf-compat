package strm.emfcompat.animationadditions.interaction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ContactTargetTest {
    @Test void differentTargetsOfOneProviderKeepTheirOwnIdentityWithoutChangingPriority() {
        var a=Candidate.single("BlockUse",Category.USE,1,1,new Candidate.Timing(.1,.2,.05),Effector.RIGHT_ARM,new float[]{1,2}).withTarget("block A").withQuietSwing(true);
        var b=a.withTarget("block B");
        assertNotEquals(a.target(),b.target());assertEquals(a.source(),b.source());
        assertEquals(a.priority(),b.priority());assertArrayEquals(a.aims().get(Effector.RIGHT_ARM),b.aims().get(Effector.RIGHT_ARM));
        assertEquals("block A",a.target());assertTrue(b.quietSwing());
    }
}
