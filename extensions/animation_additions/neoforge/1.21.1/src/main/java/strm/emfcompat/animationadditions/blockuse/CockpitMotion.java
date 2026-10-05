package strm.emfcompat.animationadditions.blockuse;

/** Return one hand before freeing the other; at least one hand stays on the rim. */
final class CockpitMotion {
    int away=-1, moving=-1;
    boolean returning;
    float progress=1;
    void advance(int request, float dt) {
        if (moving<0) {
            if (away>=0 && request!=away) {moving=away;returning=true;progress=0;}
            else if (away<0 && request>=0) {moving=request;returning=false;progress=0;}
        }
        if (moving>=0) {
            progress=Math.min(1,progress+Math.max(0,dt)/.28f);
            if (progress>=1) {away=returning ? -1 : moving;moving=-1;}
        }
    }
    float mix(int hand) {
        if (moving==hand) {
            float e=progress*progress*(3-2*progress);
            return returning ? 1-e : e;
        }
        return away==hand ? 1 : 0;
    }
    float lift(int hand) {
        if (moving!=hand) return 0;
        float s=(float)Math.sin(Math.PI*progress);
        return s*s*1.2f;
    }
    int working() {return moving>=0 ? moving : away;}
}
