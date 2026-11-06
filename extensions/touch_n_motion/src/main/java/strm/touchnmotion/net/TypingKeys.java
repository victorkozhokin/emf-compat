package strm.touchnmotion.net;

/** The last drawn key in each half, packed as two four-bit indices; zero means no key. */
public final class TypingKeys {
    private TypingKeys() {}

    public static int with(int keys, boolean right, int index) {
        int shift = right ? 0 : 4;
        int value = index < 0 || index >= 14 ? 0 : index + 1;
        return keys & ~(15 << shift) | value << shift;
    }

    public static int key(int keys, boolean right) {
        int value = keys >>> (right ? 0 : 4) & 15;
        return value == 0 || value > 14 ? -1 : value - 1;
    }
}
