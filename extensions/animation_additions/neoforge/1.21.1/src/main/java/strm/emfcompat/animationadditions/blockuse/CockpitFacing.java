package strm.emfcompat.animationadditions.blockuse;

/** Angle carrying model forward (-Z) to the projected wheel direction. */
final class CockpitFacing {
    static float angle(float x,float z) {
        return x*x+z*z<1e-6f ? 0 : (float)Math.atan2(-x,-z);
    }
    static float head(float original,float bodyTurn) {
        float degrees=original-(float)Math.toDegrees(bodyTurn);
        degrees=(degrees+180)%360;
        if(degrees<0) degrees+=360;
        return Math.max(-85,Math.min(85,degrees-180));
    }
}
