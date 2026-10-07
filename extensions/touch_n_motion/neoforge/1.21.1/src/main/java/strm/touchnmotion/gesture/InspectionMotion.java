package strm.touchnmotion.gesture;

import strm.touchnmotion.interaction.Ease;

/** A single left-foot inspection: eyes and support lead; hands settle after the foot. */
final class InspectionMotion {
    static final double SECONDS = 3.2;
    record Pose(float left, float spread, float turnLeft, float look, float support, float settle) {}

    static Pose at(float phase) {
        // Inspect two angles without setting the foot down between them. Each hold
        // has one small, slow adjustment; its velocity vanishes at both ends.
        float firstHold = breathe(phase, .41f, .51f);
        float secondHold = breathe(phase, .60f, .70f);
        float left = window(phase, .23f, .35f, .70f, .83f)
                * (1 - .025f * firstHold - .018f * secondHold);
        float spread = window(phase, .06f, .24f, .86f, .99f);
        float look = window(phase, .02f, .20f, .83f, .97f);
        float support = window(phase, .08f, .23f, .83f, .96f);
        // Left foot turns inward first, then outward to expose the other side.
        float turn = (.14f * Ease.smooth((phase - .32f) / .09f)
                - .38f * Ease.smooth((phase - .51f) / .09f)
                + .24f * Ease.smooth((phase - .70f) / .13f)
                + .012f * firstHold - .010f * secondHold) * left;
        float settle = window(phase, .82f, .88f, .90f, .98f);
        return new Pose(left, spread, left == 0 ? 0 : turn, look, support, settle);
    }

    private static float breathe(float phase, float start, float end) {
        if (phase <= start || phase >= end) return 0;
        float wave = (float) Math.sin(Math.PI * (phase - start) / (end - start));
        return wave * wave;
    }

    private static float window(float t, float start, float ready, float leave, float end) {
        return Ease.smooth((t - start) / (ready - start)) * (1 - Ease.smooth((t - leave) / (end - leave)));
    }

    private InspectionMotion() {}
}
