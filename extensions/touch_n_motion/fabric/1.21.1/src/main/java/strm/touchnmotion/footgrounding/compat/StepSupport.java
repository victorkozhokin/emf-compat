package strm.touchnmotion.footgrounding.compat;

/** The grounded hitbox has climbed; an old trailing contact must not pull it back down. */
final class StepSupport {
    private StepSupport() {}

    static float bodyDrop(float strideDrop, float rightHipDrop, float leftHipDrop,
                          double groundY, double rightPlantY, double leftPlantY,
                          boolean descending) {
        if (descending || !Double.isFinite(rightPlantY) || !Double.isFinite(leftPlantY)
                || groundY - Math.min(rightPlantY, leftPlantY) <= .2) return strideDrop;
        // Retain a small transfer of weight, and all measured surface lowering
        // (including a fence's outline). The feet keep their own landing targets.
        float supported = Math.max(0, Math.max(rightHipDrop, leftHipDrop));
        return Math.min(strideDrop, supported + 1.5f);
    }
}
