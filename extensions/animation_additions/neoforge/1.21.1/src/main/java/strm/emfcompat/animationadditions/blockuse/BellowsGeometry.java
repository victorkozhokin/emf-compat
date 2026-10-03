package strm.emfcompat.animationadditions.blockuse;
/** Renderer height is -0.125..0 blocks: two pixels of genuine compression. */
final class BellowsGeometry {
    static float height(float previous,float current,float partial) {
        return Math.max(-.125f,Math.min(0,previous+(current-previous)*Math.max(0,Math.min(1,partial))));
    }
    static float compression(float height){return Math.max(0,Math.min(1,-height*8));}
}
