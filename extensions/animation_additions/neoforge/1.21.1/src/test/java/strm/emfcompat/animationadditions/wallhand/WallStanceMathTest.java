package strm.emfcompat.animationadditions.wallhand;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;
class WallStanceMathTest {
    @Test void feetFollowTurnWithOpposingShortPlacements() {
        Vector3f a=WallStanceMath.offset(new Vector3f(-2,0,0),1);
        Vector3f b=WallStanceMath.offset(new Vector3f(2,0,0),1);
        assertTrue(a.z>0 && b.z<0);assertEquals(0,new Vector3f(a).add(b).length(),1e-5);
    }
    @Test void placementIsBoundedAcrossTheFullWalkingStride() {
        for(float z=-12;z<=12;z+=.1f)for(float yaw:new float[]{-1.2f,0,1.2f}) {
            var v=WallStanceMath.offset(new Vector3f(2,0,z),yaw);
            assertTrue(v.length()<=1.6001f);assertEquals(0,v.y);
        }
    }
    @Test void noWallDoesNotAlterFootTargetsAndInputIsRetained() {
        Vector3f v=new Vector3f(2,0,4);assertEquals(new Vector3f(),WallStanceMath.offset(v,0));assertEquals(new Vector3f(2,0,4),v);
    }
    @Test void crouchRetreatGoesBehindThePlayerWithoutSidewaysPush() {
        assertEquals(new Vector3f(0,0,2),WallStanceMath.retreat(3));
        assertEquals(new Vector3f(),WallStanceMath.retreat(-1));
        assertEquals(new Vector3f(0,0,1),WallStanceMath.retreat(1));
    }
    @Test void stepHasGentleEndpoints() {
        assertEquals(0,WallStanceMath.ease(0));assertEquals(1,WallStanceMath.ease(1));
        assertTrue(WallStanceMath.ease(.001f)<.00001f);
        assertTrue(1-WallStanceMath.ease(.999f)<.00001f);
    }
}
