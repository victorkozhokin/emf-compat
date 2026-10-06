package strm.emfcompat.animationadditions.blockuse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TypingMotionTest {
    @Test void releasedKeyLingersThenReturnsToReadiness() {
        var m = new TypingMotion();
        m.advance(3, .1);
        assertEquals(3, m.key);
        m.advance(-1, .3);
        assertEquals(3, m.key);
        m.advance(-1, .3);
        assertEquals(-1, m.key);
        for (int i = 0; i < 20; i++) m.advance(-1, .05);
        assertTrue(m.effort < .01);
    }
    @Test void heldAndSuccessiveKeysKeepTheTypingHandActive() {
        var m = new TypingMotion();
        for (int i = 0; i < 100; i++) m.advance(0, .05);
        assertEquals(0, m.key);
        assertTrue(m.effort > .99);
        m.advance(13, .05);
        assertEquals(13, m.key);
        m.advance(-1, .4);
        assertEquals(13, m.key);
        m.reset();
        assertEquals(-1, m.key);
        assertEquals(0, m.effort);
    }
    @Test void oppositeControlWaitsUntilTypingHandReturnsToRim() {
        var motion = new CockpitMotion();
        motion.advance(0, .3f);
        assertEquals(0, motion.away);
        motion.advance(1, .14f);
        assertEquals(0, motion.working());
        assertEquals(0, motion.mix(1));
        motion.advance(1, .14f);
        motion.advance(1, .14f);
        assertEquals(1, motion.working());
        assertEquals(0, motion.mix(0));
    }
}
