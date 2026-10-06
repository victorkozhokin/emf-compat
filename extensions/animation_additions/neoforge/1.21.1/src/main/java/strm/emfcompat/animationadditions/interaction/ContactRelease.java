package strm.emfcompat.animationadditions.interaction;

/** The final drawn contact outlives the provider's solver state. Time advances once per solve,
 * never on an armour copy. The brief unloading interval lets body support settle first. */
public final class ContactRelease {
    /** User-selected speed-up of the complete loss transition, including support unload. */
    public static final double SPEED = 1.5;
    public static final double UNLOAD_SECONDS = .08 / SPEED;
    private double elapsed;
    private double tau;
    private boolean active;
    public void start(double fadeSeconds) {
        elapsed = 0;
        tau = Math.max(.001, fadeSeconds) / SPEED;
        active = true;
    }
    public void cancel() { active = false; }
    public void advance(double dt) { if (active) elapsed += Math.max(0, dt); }
    public float remaining() {
        if (!active) return 0;
        // Match the old fade's settling interval (six time constants), with zero velocity
        // at both ends. An exponential starts withdrawing at its greatest speed.
        double t = Math.min(1, Math.max(0, elapsed - UNLOAD_SECONDS) / (6 * tau));
        return (float)(1 - t * t * (3 - 2 * t));
    }
    public float supportRemaining() {
        if (!active) return 0;
        return (float) Math.exp(-elapsed / tau);
    }
    public String phase() { return remaining() == 0 ? "released" : elapsed <= UNLOAD_SECONDS ? "unload" : "release"; }
}
