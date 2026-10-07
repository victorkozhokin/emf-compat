package strm.emfcompat.animationadditions.blockuse.supplementaries;

import strm.emfcompat.animationadditions.interaction.Ease;
import strm.emfcompat.animationadditions.blockuse.*;
/** Renderer height is -0.125..0 blocks: two pixels of genuine compression. */
public final class BellowsGeometry {
    static float height(float previous, float current, float partial) {
        return Math.max(-.125f, Math.min(0, previous + (current - previous) * Ease.unit(partial)));
    }
    public static float compression(float height) { return Ease.unit(-height * 8); }
}
