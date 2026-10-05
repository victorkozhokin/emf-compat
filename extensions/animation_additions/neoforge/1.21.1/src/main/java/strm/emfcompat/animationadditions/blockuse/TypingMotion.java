package strm.emfcompat.animationadditions.blockuse;

import strm.emfcompat.animationadditions.interaction.Smoothing;

/** A pressed key lingers briefly, then returns to keyboard readiness without freeing the rim. */
final class TypingMotion {
    int key=-1;double remaining;float effort;
    void advance(int pressed,double dt) {
        dt=Math.max(0,dt);
        if(pressed>=0){key=pressed;remaining=.55;}
        else {remaining=Math.max(0,remaining-dt);if(remaining==0)key=-1;}
        effort+=((key>=0?1:0)-effort)*Smoothing.follow(dt,key>=0?.10:.18);
    }
    void reset(){key=-1;remaining=0;effort=0;}
}
