package strm.touchnmotion.interaction;

/**
 * Which hand goes to a point: the main hand, unless the point is out to the other side - then the
 * hand on its side, so an arm does not reach across the chest. A hand someone else has is left to
 * them while the other one is free; a hand that cannot get there gives way to one that can.
 * Arithmetic only, no game classes: under test.
 */
public final class Handedness {

    /** How far round to the other side, radians from straight ahead, before the hand on that side takes a fresh point... */
    static final float TAKE = (float) Math.toRadians(12);
    /** ...and how far a point the hand already holds has to go round the other way to be handed over. */
    static final float KEEP = (float) Math.toRadians(20);

    private Handedness() {
    }

    /**
     * Whether the right hand goes to a point at {@code x}, {@code z} model pixels from the middle of
     * the body (to the right is -x, ahead -z).
     *
     * @param mainRight whether the main hand is the right one
     * @param held      the hand on the point now, {@code true} the right, {@code null} none
     * @param rightBusy whether someone else has the right hand
     * @param leftBusy  the same for the left
     * @param rightCan  whether the right hand gets there at all
     * @param leftCan   the same for the left
     * @param nearest   whether the hand on the point's side is wanted, or always the main one
     */
    public static boolean right(float x, float z, boolean mainRight, Boolean held,
                                boolean rightBusy, boolean leftBusy, boolean rightCan, boolean leftCan, boolean nearest) {
        boolean right = nearest ? side(x, z, held != null ? held : mainRight, held != null) : mainRight;
        // Taken by someone else while the other hand is free: the other one.
        if (right ? rightBusy && !leftBusy : leftBusy && !rightBusy) right = !right;
        // Out of reach of this hand but not of the other.
        if (right ? !rightCan && leftCan : !leftCan && rightCan) right = !right;
        return right;
    }

    /** The side of the point, leaning to {@code preferRight}'s hand by {@link #TAKE}, or {@link #KEEP} when it holds the point. */
    static boolean side(float x, float z, boolean preferRight, boolean holding) {
        // Round from straight ahead, to the right positive; beside the body it is a right angle.
        double round = Math.atan2(-x, -z);
        double lean = holding ? KEEP : TAKE;
        return round + (preferRight ? lean : -lean) > 0;
    }
}
