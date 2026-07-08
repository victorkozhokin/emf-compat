package strm.emfcompat.core;

/**
 * Describes which player limbs are currently animated by an external animation mod.
 */
public record ActiveParts(boolean leftArm, boolean rightArm, boolean leftLeg, boolean rightLeg) {

    /**
     * Convenience constructor for arm-only animations.
     */
    public ActiveParts(boolean leftArm, boolean rightArm) {
        this(leftArm, rightArm, false, false);
    }
}
