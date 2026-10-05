package strm.emfcompat.animationadditions.blockuse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TableSupportTest {
    @Test void diagonalAndCardinalApproachesKeepBothPalmsInside() {
        var table=new TableSurface(0,1,0,1,.8125,.1875);
        for(double x:new double[]{-1,.5,2})for(double z:new double[]{-1,.5,2}) {
            var r=table.contact(x,z,true);var l=table.contact(x,z,false);
            for(var p:new TableSurface.Point[]{r,l}){assertTrue(p.x()>=.1875 && p.x()<=.8125);assertTrue(p.z()>=.1875 && p.z()<=.8125);assertEquals(.8125,p.y());}
            assertTrue(Math.hypot(r.x()-l.x(),r.z()-l.z())>.35);
        }
    }
    @Test void differentlySizedTableHasItsOwnHeightAndSafeInset() {
        var table=new TableSurface(-.5,1.5,-.25,1.25,.5,.2);
        var p=table.contact(3,0,true);assertTrue(p.x()<=1.3 && p.x()>.5);assertEquals(.5,p.y());
        assertThrows(IllegalArgumentException.class,()->new TableSurface(0,.2,0,1,.5,.2));
    }
    @Test void noWeightBeforeSettledPalmContact() {
        var m=new TableSupportMotion();for(int i=0;i<20;i++)m.advance(.05f,true,false);assertEquals(0,m.load);
        m.advance(.05f,true,true);m.advance(.05f,true,true);assertEquals(0,m.load);
        for(int i=0;i<10;i++)m.advance(.05f,true,true);assertEquals(1,m.load);
    }
    @Test void UnloadFinishesBeforeTheRetainedPalmLetsGo() {
        var m=new TableSupportMotion();m.load=1;m.advance(.05f,false,true);assertTrue(m.load>0 && m.load<1);
        for(int i=0;i<4;i++)m.advance(.05f,false,true);assertEquals(0,m.load);assertEquals(0,m.settled);
    }
    @Test void lostContactCannotContinueLoading() {
        var m=new TableSupportMotion();m.load=.5f;m.advance(.05f,true,false);assertTrue(m.load<.5f);assertEquals(0,m.settled);
    }
}
