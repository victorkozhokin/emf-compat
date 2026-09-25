package strm.emfcompat.animationadditions.interaction;

/**
 * A part of the body one interaction at a time can own. Each is a model part by its vanilla name;
 * an arm's roll is levelled as it is aimed, the head keeps its own.
 */
public enum Effector {
    RIGHT_ARM("right_arm", true),
    LEFT_ARM("left_arm", true),
    HEAD("head", false);

    public final String part;
    /** Whether aiming this part also levels its roll (zRot to 0). */
    public final boolean levelsRoll;

    Effector(String part, boolean levelsRoll) {
        this.part = part;
        this.levelsRoll = levelsRoll;
    }

    public boolean isArm() {
        return this == RIGHT_ARM || this == LEFT_ARM;
    }
}
