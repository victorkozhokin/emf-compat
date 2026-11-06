package strm.touchnmotion.net;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TypingKeysTest {
    @Test void simultaneousHandsAndTheirLastKeysSurvive() {
        int keys = TypingKeys.with(0, true, 3);
        keys = TypingKeys.with(keys, false, 11);
        keys = TypingKeys.with(keys, true, 0);
        assertEquals(0, TypingKeys.key(keys, true));
        assertEquals(11, TypingKeys.key(keys, false));
        keys = TypingKeys.with(keys, true, -1);
        assertEquals(-1, TypingKeys.key(keys, true));
        assertEquals(11, TypingKeys.key(keys, false));
    }

    @Test void allFourteenKeysFitAndNoKeyStaysUnset() {
        for (int i = 0; i < 14; i++) {
            int keys = TypingKeys.with(TypingKeys.with(0, true, i), false, 13 - i);
            assertEquals(i, TypingKeys.key(keys, true));
            assertEquals(13 - i, TypingKeys.key(keys, false));
            assertTrue(keys >= 0 && keys < 256);
        }
        assertEquals(-1, TypingKeys.key(0, true));
        assertEquals(-1, TypingKeys.key(0, false));
    }
}
