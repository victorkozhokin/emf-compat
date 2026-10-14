package strm.touchnmotion.blockuse.aeronautics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ControlHandTest {
    @Test void anActiveKeyboardKeepsItsHandThroughInclinesAndNoisySideSamples() {
        var hand = new ControlHand();
        assertEquals(0, hand.choose("keyboard", .5));
        for (double side : new double[]{.001, -.001, -.5, 0, .5}) assertEquals(0, hand.choose("keyboard", side));
        assertEquals(1, hand.choose("other keyboard", -.5));
        assertEquals(-1, hand.choose(null, 0));
        assertEquals(1, hand.choose("keyboard", -.5));
    }
}
