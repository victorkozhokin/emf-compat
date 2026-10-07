package strm.emfcompat.animationadditions.blockuse;

import strm.emfcompat.animationadditions.interaction.Ease;
/** Renderer height is -0.125..0 blocks: two pixels of genuine compression. */
final class BellowsGeometry {
    static float height(float previous, float current, float partial) {
        return Math.max(-.125f, Math.min(0, previous + (current - previous) * Ease.unit(partial)));
    }
    static float compression(float height) { return Ease.unit(-height * 8); }
}
