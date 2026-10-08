package strm.touchnmotion.interaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides who owns each part this frame. Pure logic, no game classes, so it is unit-tested.
 *
 * <p>Candidates go by priority; a candidate takes its parts only if all of them are still free
 * (not reserved by something outside, not taken by a stronger candidate). At equal priority the
 * source already holding the parts keeps them unless a rival is more confident by more than
 * {@link #HYSTERESIS}, so two near-equal targets do not trade the arm every frame; after that,
 * confidence, then the source name, so the result never depends on the order the candidates came
 * in.</p>
 */
public final class Arbiter {

    public static final float HYSTERESIS = 0.15f;

    private Arbiter() {
    }

    /**
     * @param candidates what the providers offer this frame
     * @param reserved   parts something outside the runtime owns (a pose from another addon, a
     *                   swing): nobody gets them
     * @param holders    who held each part last frame, by source
     * @return the winning candidate of each part that has one
     */
    public static Map<Effector, Candidate> resolve(List<Candidate> candidates, Set<Effector> reserved,
                                                   Map<Effector, String> holders) {
        List<Candidate> order = new ArrayList<>(candidates);
        order.sort(Comparator.comparingInt(Candidate::priority).reversed()
                .thenComparing(Comparator.comparingDouble((Candidate c) -> standing(c, holders)).reversed())
                .thenComparing(Candidate::source));

        Map<Effector, Candidate> owners = new EnumMap<>(Effector.class);
        for (Candidate candidate : order) {
            if (candidate.aims().isEmpty()) continue;
            boolean free = true;
            for (Effector effector : candidate.aims().keySet()) {
                if (reserved.contains(effector) || owners.containsKey(effector)) {
                    free = false;
                    break;
                }
            }
            if (!free) continue;
            for (Effector effector : candidate.aims().keySet()) owners.put(effector, candidate);
        }
        return owners;
    }

    /** Confidence, with the edge a candidate gets for already holding one of its parts. */
    private static double standing(Candidate candidate, Map<Effector, String> holders) {
        for (Effector effector : candidate.aims().keySet()) {
            if (candidate.source().equals(holders.get(effector))) return candidate.confidence() + HYSTERESIS;
        }
        return candidate.confidence();
    }
}
