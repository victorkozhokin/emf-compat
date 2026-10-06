package strm.emfcompat.animationadditions.buttonpress;

/** Bounded visual effort from observed lever travel; never modifies the lever's physics. */
final class ThrottleEffort {
    float load, assist, recoil, regrip;
    int direction;
    private int previous;
    private boolean initialized, moving, released;
    private double age, quiet = 1, travel, phase = 1, cooldown;
    void advance(Integer signal, boolean held, boolean helperReady, double dt, boolean fresh) {
        dt = Math.max(0, Math.min(.1, dt));
        if (fresh || !initialized) {
            previous = signal == null ? 0 : signal;
            initialized = true;
            age = travel = 0;
            quiet = 1;
            phase = 1;
            cooldown = 0;
            moving = false;
            released = false;
            load = assist = recoil = regrip = 0;
            direction = 0;
        }
        age += dt;
        quiet += dt;
        cooldown = Math.max(0, cooldown - dt);
        int change = signal == null || !held ? 0 : signal - previous;
        if (signal != null) previous = signal;
        if (change != 0) {
            int next = change > 0 ? 1 : -1;
            if (direction != 0 && next != direction) recoil = -direction * .28f;
            direction = next;
            travel += Math.abs(change);
            quiet = 0;
            moving = true;
            released = false;
        }
        if (moving && quiet > .18 || !held && !released) {
            if (load > .25f) recoil = -direction * load * .22f;
            moving = false;
            released = true;
        }
        load += (held && quiet < .18 ? 1 - load : -load) * follow(dt, .12);
        float wanted = held && age > .18 && travel >= 3 && quiet < .4 ? 1 : 0;
        if (phase < 1 && helperReady) wanted = 1;
        assist += (wanted - assist) * follow(dt, .13);
        if (held && helperReady && load > .25f && travel >= 8 && phase >= 1 && cooldown == 0) {
            phase = 0;
            cooldown = 1.4;
            travel = 0;
        }
        if (!held || !helperReady) phase = 1;
        phase = Math.min(1, phase + dt / .34);
        regrip = phase >= 1 ? 0 : (float) Math.sin(Math.PI * phase);
        recoil *= (float) Math.exp(-dt / .17);
    }
    private static float follow(double dt, double tau) { return (float) - Math.expm1(-dt / tau); }
}
