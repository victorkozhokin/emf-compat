package strm.touchnmotion.lead;

/** One short invitation after a real walking-to-stop transition, with speed hysteresis. */
final class LeadStopGesture {
    private double walking, stopped, phase = 1;
    private boolean armed;
    float advance(double speed, double distance, double dt, boolean reset) {
        dt = Math.max(0, Math.min(.1, dt));
        if (reset) { walking = stopped = 0; armed = false; phase = 1; return 0; }
        if (speed > .8) {
            walking += dt;
            stopped = 0;
            if (walking > .15) armed = true;
        } else if (speed < .25) {
            walking = 0;
            stopped += dt;
            if (armed && stopped > .08) {
                armed = false;
                if (distance > 2 && distance < 9.5) phase = 0;
            }
        } else stopped = 0;
        phase = Math.min(1, phase + dt / .42);
        return (float) Math.sin(Math.PI * phase);
    }
}
