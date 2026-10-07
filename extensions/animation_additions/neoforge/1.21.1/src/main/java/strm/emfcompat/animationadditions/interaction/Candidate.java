package strm.emfcompat.animationadditions.interaction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * One interaction a provider offers for this frame: which parts it wants and where to aim each.
 *
 * <p>The parts are one group: the candidate wins them all or none (two hands on a wall, not one).
 * An aim is {xRot, yRot} in model space for the part. {@code confidence} (0..1) orders candidates
 * of equal priority; the one already holding its parts keeps them unless another is clearly
 * better ({@link Arbiter#HYSTERESIS}).</p>
 */
public record Candidate(String source, Category category, int priority, Map<Effector, float[]> aims,
                        float confidence, Timing timing, Object target, boolean quietSwing) {

    public Candidate(String source, Category category, int priority, Map<Effector, float[]> aims,
                     float confidence, Timing timing) {
        this(source, category, priority, aims, confidence, timing, null, false);
    }
    public Candidate withTarget(Object identity) {
        return new Candidate(source, category, priority, aims, confidence, timing, identity, quietSwing);
    }

    public Candidate withQuietSwing(boolean quiet) {
        return new Candidate(source, category, priority, aims, confidence, timing, target, quiet);
    }

    public Candidate {
        aims = Collections.unmodifiableMap(new EnumMap<>(aims));
    }

    public static Candidate of(String source, Category category, int offset, float confidence, Timing timing,
                               Map<Effector, float[]> aims) {
        return new Candidate(source, category, category.priority(offset), aims, confidence, timing);
    }

    public static Candidate single(String source, Category category, int offset, float confidence, Timing timing,
                                   Effector effector, float[] aim) {
        Map<Effector, float[]> aims = new EnumMap<>(Effector.class);
        aims.put(effector, aim);
        return of(source, category, offset, confidence, timing, aims);
    }

    /**
     * How a winning candidate shows: seconds to fade in and out (time constants of an
     * exponential approach, the same at any frame rate) and how quickly the aim follows a moving
     * target, 0 for at once. A fade-out of 0 lets go at once.
     */
    public record Timing(double fadeIn, double fadeOut, double aimSeconds) {
    }
}
