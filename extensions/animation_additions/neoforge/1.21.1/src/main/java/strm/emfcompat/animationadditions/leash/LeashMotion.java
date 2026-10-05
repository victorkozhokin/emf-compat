package strm.emfcompat.animationadditions.leash;

/** Visual anticipation of vanilla's six-block elastic leash, without changing its physics. */
final class LeashMotion {
    float load, jerk;
    private float previousSpeed;
    private boolean initialized;

    void advance(double distance, double outwardSpeed, double dt, boolean fresh) {
        float speed = (float)Math.max(0, Math.min(12, outwardSpeed));
        if (fresh || !initialized) { previousSpeed=speed;jerk=0;initialized=true; }
        float taut = smooth((float)((distance-4.5)/1.5));
        // Compare with a slow relative-speed baseline; packet/tick noise must not become
        // an acceleration impulse on every frame of otherwise constant-speed walking.
        float impulse = dt>1e-4 && !fresh ? Math.max(0,speed-previousSpeed-.75f) : 0;
        float wantedJerk = Math.min(1,impulse/3)*taut;
        float follow = dt<=0 ? 0 : (float)-Math.expm1(-Math.min(.1,dt)/.12);
        load += (taut-load)*follow;
        jerk = Math.max(wantedJerk,jerk*(float)Math.exp(-Math.max(0,dt)/.22));
        previousSpeed+=(speed-previousSpeed)*(float)-Math.expm1(-Math.max(0,dt)/.25);
    }
    static float smooth(float v) { v=Math.max(0,Math.min(1,v));return v*v*(3-2*v); }
}
