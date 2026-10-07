package strm.touchnmotion.interaction;

public final class ContactReach {
    private ContactReach() {}
    /** Arm-length units: 0.06 of an eleven-pixel arm is 0.041 blocks. */
    public static boolean accepts(float reach, float limit, boolean held) {
        return Float.isFinite(reach) && reach <= limit + (held ? .06f : 0);
    }
}
