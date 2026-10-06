package strm.emfcompat.animationadditions.gesture;

/** A single left-foot inspection: eyes and support lead; hands settle after the foot. */
final class InspectionMotion {
    static final double SECONDS = 2.6;
    record Pose(float left, float spread, float turnLeft, float look, float support, float settle) {}

    static Pose at(float phase) {
        float left = window(phase, .23f, .36f, .57f, .74f);
        float spread = window(phase, .06f, .24f, .78f, .98f);
        float look = window(phase, .02f, .20f, .74f, .94f);
        float support = window(phase, .08f, .23f, .74f, .92f);
        float turn = -.25f * (float) Math.sin(Math.PI * smooth((phase - .31f) / .35f)) * left;
        float settle = window(phase, .73f, .80f, .83f, .94f);
        return new Pose(left, spread, turn, look, support, settle);
    }

    private static float window(float t, float start, float ready, float leave, float end) {
        return smooth((t - start) / (ready - start)) * (1 - smooth((t - leave) / (end - leave)));
    }

    private static float smooth(float v) {
        v = Math.max(0, Math.min(1, v));
        return v * v * (3 - 2 * v);
    }

    private InspectionMotion() {}
}
