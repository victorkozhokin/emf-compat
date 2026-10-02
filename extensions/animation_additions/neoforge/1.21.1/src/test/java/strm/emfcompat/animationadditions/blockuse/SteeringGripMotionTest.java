package strm.emfcompat.animationadditions.blockuse;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SteeringGripMotionTest {
    @Test void valveAndWheelContactsRemainOnSeparatedHalvesForAllMountsAndTurns() {
        for (org.joml.Vector3f facing : new org.joml.Vector3f[]{
                new org.joml.Vector3f(1,0,0),new org.joml.Vector3f(-1,0,0),
                new org.joml.Vector3f(0,0,1),new org.joml.Vector3f(0,0,-1),
                new org.joml.Vector3f(0,1,0),new org.joml.Vector3f(0,-1,0)}) {
            var centre=WheelGeometry.valve(new org.joml.Vector3f(.5f,6.5f/16f,.5f),facing,0);
            var side=WheelGeometry.valve(new org.joml.Vector3f(14f/16f,6.5f/16f,.5f),facing,0).sub(centre).normalize();
            SteeringGripMotion motion=new SteeringGripMotion();
            for (int i=0;i<2400;i++) {
                motion.advance((float)Math.toRadians(i<1200 ? i : 2400-i),.016f);
                var right=WheelGeometry.valve(new org.joml.Vector3f(14f/16f,6.5f/16f,.5f),facing,motion.radians(0)).sub(centre);
                var left=WheelGeometry.valve(new org.joml.Vector3f(2f/16f,6.5f/16f,.5f),facing,motion.radians(1)).sub(centre);
                assertTrue(right.dot(side)>.12f,"Right hand approaches hub/crosses half");
                assertTrue(left.dot(side)<-.12f,"Left hand approaches hub/crosses half");
                assertEquals(.375f,right.length(),1e-5f);
                assertEquals(.375f,left.length(),1e-5f);
                assertFalse(motion.lift(0)>0 && motion.lift(1)>0);
            }
        }
    }

    @Test void rimSideIsAssignedFromApproachAndNotCameraYaw() {
        assertTrue(WheelGeometry.positiveSide(1,0,0,-1));
        assertFalse(WheelGeometry.positiveSide(1,0,0,1));
        assertTrue(WheelGeometry.positiveSide(0,1,1,0));
        assertFalse(WheelGeometry.positiveSide(0,1,-1,0));
    }

    @Test void sixAnchorsRegripOneHandAtATimeWithoutCrossingThroughRepeatedTurnsAndReversal() {
        SteeringGripMotion motion = new SteeringGripMotion();
        boolean[] slots = new boolean[6];
        for (int i=0; i<=2880; i++) {
            float angle = i<=1440 ? i*.5f : 720-(i-1440)*.5f;
            motion.advance((float)Math.toRadians(angle),.02f);
            assertFalse(motion.lift(0)>1e-5 && motion.lift(1)>1e-5,"Both hands released at "+angle);
            for (int hand=0;hand<2;hand++) {
                assertTrue(Math.abs(motion.radians(hand))<=Math.toRadians(85)+1e-5);
                if (hand != motion.moving()) {
                    float phase = (float)Math.toDegrees(motion.radians(hand));
                    float relative = CrankStanceMath.delta(angle,phase);
                    assertEquals(Math.round(relative/60)*60,relative,1e-3,"Planted hand left its anchor");
                    slots[motion.slot(hand)] = true;
                }
            }
        }
        assertTrue(motion.transfers>20);
        for(boolean visited:slots)assertTrue(visited,"Not all six anchors used");
    }

    @Test void preTurnedWheelAcquiresANearbyAnchorAndAngleWrapStaysContinuous() {
        SteeringGripMotion motion = new SteeringGripMotion();
        motion.advance((float)Math.toRadians(359),.02f);
        assertEquals(-1,Math.toDegrees(motion.radians(0)),1e-3);
        motion.advance((float)Math.toRadians(1),.02f);
        assertEquals(1,Math.toDegrees(motion.radians(0)),1e-3);
        assertEquals(0,motion.transfers);
    }

    @Test void lowWheelRegripsStayInTheUpperQuadrantsWithOnePlantedHand() {
        for(float upper:new float[]{-45,45}) {
            SteeringGripMotion motion=new SteeringGripMotion(upper);
            for(int i=0;i<=1440;i++) {
                float angle=i<720 ? i*.5f : 360-(i-720)*.5f;
                motion.advance((float)Math.toRadians(angle),.02f);
                assertFalse(motion.lift(0)>0 && motion.lift(1)>0);
                assertTrue(motion.radians(0)*upper>0);
                assertTrue(motion.radians(1)*upper<0);
                for(int hand=0;hand<2;hand++) if(hand!=motion.moving()) {
                    float relative=CrankStanceMath.delta(angle,(float)Math.toDegrees(motion.radians(hand)));
                    assertEquals(Math.round(relative/60)*60,relative,1e-3);
                }
            }
        }
    }

    @Test void rapidInputAndReversalKeepOneHandOnItsOwnHalf() {
        SteeringGripMotion motion=new SteeringGripMotion();
        for(float angle:new float[]{0,90,180,270,0,-90,-180,0}) {
            motion.advance((float)Math.toRadians(angle),.02f);
            assertFalse(motion.lift(0)>0 && motion.lift(1)>0);
            assertTrue(Math.abs(motion.radians(0))<Math.PI/2);
            assertTrue(Math.abs(motion.radians(1))<Math.PI/2);
        }
    }
}
