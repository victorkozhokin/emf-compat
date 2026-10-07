package strm.emfcompat.animationadditions.buttonpress;

import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;
import strm.emfcompat.animationadditions.interaction.Ease;

public class ReachEnvelopeTest {
    @Test void contactCorrectionBlendsInAndOutWithoutSnapping() {
        var current = new org.joml.Quaternionf();
        var target = new org.joml.Quaternionf().rotationX((float) Math.toRadians(25));
        ReachEnvelope.followContact(current, target, 1.0 / 60);
        float angle = current.angle();
        assertTrue(angle > 0 && angle < Math.toRadians(3));
        ReachEnvelope.followContact(current, new org.joml.Quaternionf(), 1.0 / 60);
        assertTrue(current.angle() > 0 && current.angle() < angle);
        var slow = new org.joml.Quaternionf();
        var fast = new org.joml.Quaternionf();
        for (int i = 0; i < 30; i++) ReachEnvelope.followContact(slow, target, 1.0 / 30);
        for (int i = 0; i < 144; i++) ReachEnvelope.followContact(fast, target, 1.0 / 144);
        assertEquals(slow.angle(), fast.angle(), 1e-4);
    }
    @Test void lowOrReachableTargetsDoNotUncrouch() {
        assertEquals(0, ReachEnvelope.upright(0, 14, 8, 11));
        assertEquals(0, ReachEnvelope.upright(0, -2, 8, 11));
        assertEquals(0, ReachEnvelope.upright(0, 0, 15, 11));
    }
    @Test void overheadExtensionIsContinuousAndBounded() {
        float last = 0;
        for (float y = 0; y > -25; y -= 0.01f) {
            float w = ReachEnvelope.upright(0, y, 8, 11);
            assertTrue(w >= last - 1e-5 && w <= 1);
            assertTrue(w - last < 0.01);
            last = w;
        }
        assertEquals(1, last);
    }
    @Test void noSnapOnFirstFrameAndMonotonicRelease() {
        assertEquals(0, ReachEnvelope.follow(0, 1, 0));
        float w = ReachEnvelope.follow(0, 1, 1.0 / 60);
        assertTrue(w > 0 && w < 0.15);
        for (int i = 0; i < 60; i++) {
            float next = ReachEnvelope.follow(w, 0, 1.0 / 60);
            assertTrue(next >= 0 && next <= w);
            w = next;
        }
        assertTrue(w < 0.002);
    }
    @Test void transitionsAgreeAtThirtySixtyAndOneFortyFourFps() {
        for (int fps : new int[]{30, 60, 144}) {
            float w = 0;
            for (int i = 0; i < fps; i++) w = ReachEnvelope.follow(w, 1, 1.0 / fps);
            assertEquals(1 - Math.exp(-1 / 0.16), w, 2e-6);
            for (int i = 0; i < fps; i++) w = ReachEnvelope.follow(w, 0, 1.0 / fps);
            assertEquals((1 - Math.exp(-1 / 0.16)) * Math.exp(-1 / 0.24), w, 2e-6);
        }
    }
    @Test void preparationHasZeroSlopeAtBothEndpoints() {
        assertEquals(0, Ease.smooth(-1));
        assertEquals(1, Ease.smooth(2));
        assertTrue(Ease.smooth(0.0001f) < 1e-6);
        assertTrue(1 - Ease.smooth(0.9999f) < 1e-6);
    }
    @Test void contactTurnReachesWithoutStretchingTheTorso() {
        Vector3f shoulder = new Vector3f(-5, -12, 0);
        Vector3f target = new Vector3f(6, -17, -5);
        var turn = ReachEnvelope.contactTurn(shoulder, target, 11, 0.5f);
        Vector3f moved = turn.transform(new Vector3f(shoulder));
        assertEquals(shoulder.length(), moved.length(), 1e-5);
        assertEquals(11, moved.distance(target), 1e-4);
        assertTrue(turn.angle() < 0.5f);
    }
    @Test void contactTurnLeavesReachableTargetsAndCapsUnreachableOnes() {
        Vector3f shoulder = new Vector3f(0, -12, 0);
        assertEquals(0, ReachEnvelope.contactTurn(shoulder, new Vector3f(0, -12, -5), 11, .3f).angle());
        var limited = ReachEnvelope.contactTurn(shoulder, new Vector3f(30, -12, -10), 11, .3f);
        assertEquals(.3f, limited.angle(), 1e-5);
        assertTrue(limited.transform(new Vector3f(shoulder)).distance(new Vector3f(30, -12, -10))
 < shoulder.distance(new Vector3f(30, -12, -10)));
    }
    @Test void oppositeAndDegenerateContactDirectionsStayFinite() {
        assertTrue(ReachEnvelope.contactTurn(new Vector3f(0, -12, 0), new Vector3f(0, 20, 0), 11, .3f).isFinite());
        assertEquals(0, ReachEnvelope.contactTurn(new Vector3f(), new Vector3f(0, 20, 0), 11, .3f).angle());
    }
}
