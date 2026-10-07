package strm.touchnmotion.interaction;

/**
 * What kind of interaction a candidate is, and so the band its priority falls in: a combat move
 * beats climbing, climbing beats pressing a button, and so on down to idle glances. A provider
 * picks a priority inside its band ({@link #priority(int)}) to order its own kinds.
 */
public enum Category {
    COMBAT(500),
    TRAVERSAL(400),
    ACTIVE(300),
    USE(250),
    PASSIVE(100),
    IDLE(0);

    public final int base;

    Category(int base) {
        this.base = base;
    }

    /** A priority inside this band, {@code offset} 0..49. */
    public int priority(int offset) {
        return base + Math.max(0, Math.min(49, offset));
    }
}
