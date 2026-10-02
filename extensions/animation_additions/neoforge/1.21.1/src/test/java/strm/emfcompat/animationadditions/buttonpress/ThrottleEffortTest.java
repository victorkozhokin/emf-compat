package strm.emfcompat.animationadditions.buttonpress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ThrottleEffortTest {
    @Test void idleAndFreshSignalDoNotInventResistance() {
        ThrottleEffort m=new ThrottleEffort();
        for(int i=0;i<200;i++)m.advance(15,true,false,.02,i==0);
        assertEquals(0,m.load);assertEquals(0,m.assist);assertEquals(0,m.regrip);assertEquals(0,m.recoil);
    }
    @Test void movementLoadsAndBothStoppingAndReleaseSettle() {
        ThrottleEffort m=new ThrottleEffort();m.advance(0,true,false,.02,true);
        for(int i=1;i<=8;i++)for(int j=0;j<4;j++)m.advance(i,true,false,.02,false);
        assertTrue(m.load>.9);assertTrue(m.assist>.8);
        for(int i=0;i<100;i++)m.advance(8,false,false,.02,false);
        assertTrue(m.load<.001 && m.assist<.001 && Math.abs(m.recoil)<.001);assertEquals(0,m.regrip);
    }
    @Test void reversalAddsBoundedRecoilInOppositeDirection() {
        ThrottleEffort m=new ThrottleEffort();m.advance(0,true,false,.02,true);
        for(int i=1;i<8;i++)m.advance(i,true,false,.05,false);
        m.advance(6,true,false,.02,false);
        assertEquals(-1,m.direction);assertTrue(m.recoil<0 && m.recoil>-.3);
    }
    @Test void regripRequiresAnAlreadyPlantedHelperAndNeverRepeatsAtRest() {
        ThrottleEffort m=new ThrottleEffort();m.advance(0,true,false,.02,true);
        for(int i=1;i<=10;i++)m.advance(i,true,false,.05,false);
        assertEquals(0,m.regrip);
        m.advance(11,true,true,.02,false);assertTrue(m.regrip>0);
        for(int i=0;i<100;i++)m.advance(11,true,true,.02,false);
        assertEquals(0,m.regrip);assertTrue(m.assist<.001);
    }
    @Test void losingHelperCancelsPrimaryReleaseImmediately() {
        ThrottleEffort m=new ThrottleEffort();m.advance(0,true,false,.02,true);
        for(int i=1;i<=10;i++)m.advance(i,true,true,.05,false);
        assertTrue(m.regrip>0);
        m.advance(10,true,false,.02,false);assertEquals(0,m.regrip);
    }
    @Test void switchingLeversResetsTravelAndRecoil() {
        ThrottleEffort m=new ThrottleEffort();m.advance(0,true,false,.02,true);
        for(int i=1;i<=10;i++)m.advance(i,true,true,.05,false);
        m.advance(0,true,true,.02,true);
        assertEquals(0,m.load);assertEquals(0,m.assist);assertEquals(0,m.recoil);assertEquals(0,m.regrip);
    }
    @Test void longRepeatedReversalsRemainFiniteAndBoundedAtAllFrameRates() {
        for(int fps:new int[]{30,60,144}) {
            ThrottleEffort m=new ThrottleEffort();
            for(int i=0;i<fps*10;i++) {
                int signal=(int)(7.5+7.5*Math.sin(i*1.0/fps*4));
                m.advance(signal,true,true,1.0/fps,i==0);
                assertTrue(m.load>=0 && m.load<=1 && m.assist>=0 && m.assist<=1);
                assertTrue(Math.abs(m.recoil)<=.3 && m.regrip>=0 && m.regrip<=1);
            }
        }
    }
}
