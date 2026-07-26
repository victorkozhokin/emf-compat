package strm.emfcompat.create.flight;

/**
 * Tracks whether the currently loaded animation pack actually has a flight
 * animation: either it reads {@code nbt(abilities.flying, ...)} directly, or it
 * defines a {@code var.fly} / {@code varb.fly} variable.
 *
 * <p>The jetpack spoofing mixins are gated on this flag: if the pack has no
 * flight animation, spoofing {@code abilities.flying} and suppressing
 * {@code is_jumping} would only remove animations (jump/fall chain) without any
 * flight animation replacing them, leaving the model broken mid-air.</p>
 *
 * <p>The flag is recomputed on every resource reload: reset from
 * {@code EMFManager.resetInstance()} and raised by {@code FlightAnimationDetectionMixin}
 * when the player model's animation handler is received.</p>
 */
public final class FlightAnimationSupport {

    private static volatile boolean packFlightAware;

    private FlightAnimationSupport() {
    }

    /** Whether the active pack contains a flight animation for the player model. */
    public static boolean isPackFlightAware() {
        return packFlightAware;
    }

    /** Called at the start of an EMF reload, before any model is set up. */
    public static void reset() {
        packFlightAware = false;
    }

    /**
     * Inspects one animation line of the player model and raises the flag if it
     * is flight-related. Idempotent; never lowers the flag once raised.
     */
    public static void inspectLine(String animKey, String expression, boolean isVar) {
        if (packFlightAware) return;
        if (expression != null && expression.contains("abilities.flying")) {
            packFlightAware = true;
            return;
        }
        if (isVar && animKey != null && (animKey.equals("var.fly") || animKey.equals("varb.fly"))) {
            packFlightAware = true;
        }
    }
}
