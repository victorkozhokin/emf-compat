package strm.emfcompat.animationadditions.interaction;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArbiterTest {

    private static final Candidate.Timing T = new Candidate.Timing(0.1, 0.1, 0);
    private static final Set<Effector> NONE = EnumSet.noneOf(Effector.class);
    private static final Map<Effector, String> NOBODY = new EnumMap<>(Effector.class);

    private static Candidate one(String source, Category category, int offset, float confidence, Effector effector) {
        return Candidate.single(source, category, offset, confidence, T, effector, new float[]{0, 0});
    }

    private static Candidate both(String source, Category category, int offset) {
        Map<Effector, float[]> aims = new EnumMap<>(Effector.class);
        aims.put(Effector.RIGHT_ARM, new float[]{0, 0});
        aims.put(Effector.LEFT_ARM, new float[]{0, 0});
        return Candidate.of(source, category, offset, 1f, T, aims);
    }

    @Test
    void higherPriorityWins() {
        Candidate plant = one("plant", Category.PASSIVE, 10, 1f, Effector.RIGHT_ARM);
        Candidate wall = one("wall", Category.PASSIVE, 20, 0.1f, Effector.RIGHT_ARM);
        Map<Effector, Candidate> owners = Arbiter.resolve(List.of(plant, wall), NONE, NOBODY);
        assertSame(wall, owners.get(Effector.RIGHT_ARM));
    }

    @Test
    void categoryBeatsOffset() {
        Candidate idle = one("look", Category.IDLE, 49, 1f, Effector.HEAD);
        Candidate active = one("button", Category.ACTIVE, 0, 0f, Effector.HEAD);
        assertSame(active, Arbiter.resolve(List.of(idle, active), NONE, NOBODY).get(Effector.HEAD));
    }

    @Test
    void aGroupWinsWholeOrNotAtAll() {
        // Both hands on the wall ahead lose the left one to something stronger: they lose both.
        Candidate wall = both("wall", Category.PASSIVE, 20);
        Candidate swing = one("combat", Category.COMBAT, 0, 1f, Effector.LEFT_ARM);
        Candidate plant = one("plant", Category.PASSIVE, 10, 1f, Effector.RIGHT_ARM);
        Map<Effector, Candidate> owners = Arbiter.resolve(List.of(wall, swing, plant), NONE, NOBODY);
        assertSame(swing, owners.get(Effector.LEFT_ARM));
        assertSame(plant, owners.get(Effector.RIGHT_ARM), "the freed right arm goes to the next one");
    }

    @Test
    void reservedPartsGoToNobody() {
        Candidate wall = both("wall", Category.PASSIVE, 20);
        Candidate look = one("look", Category.IDLE, 10, 1f, Effector.HEAD);
        Set<Effector> arms = EnumSet.of(Effector.RIGHT_ARM, Effector.LEFT_ARM);
        Map<Effector, Candidate> owners = Arbiter.resolve(List.of(wall, look), arms, NOBODY);
        assertFalse(owners.containsKey(Effector.RIGHT_ARM));
        assertFalse(owners.containsKey(Effector.LEFT_ARM));
        assertSame(look, owners.get(Effector.HEAD), "a reservation of the arms leaves the head alone");
    }

    @Test
    void theHolderKeepsItAgainstASlightlyBetterRival() {
        Candidate held = one("a", Category.PASSIVE, 10, 0.50f, Effector.RIGHT_ARM);
        Candidate rival = one("b", Category.PASSIVE, 10, 0.60f, Effector.RIGHT_ARM);
        Map<Effector, String> holders = new EnumMap<>(Effector.class);
        holders.put(Effector.RIGHT_ARM, "a");
        assertSame(held, Arbiter.resolve(List.of(rival, held), NONE, holders).get(Effector.RIGHT_ARM));
    }

    @Test
    void aClearlyBetterRivalTakesOver() {
        Candidate held = one("a", Category.PASSIVE, 10, 0.2f, Effector.RIGHT_ARM);
        Candidate rival = one("b", Category.PASSIVE, 10, 0.2f + Arbiter.HYSTERESIS + 0.05f, Effector.RIGHT_ARM);
        Map<Effector, String> holders = new EnumMap<>(Effector.class);
        holders.put(Effector.RIGHT_ARM, "a");
        assertSame(rival, Arbiter.resolve(List.of(held, rival), NONE, holders).get(Effector.RIGHT_ARM));
    }

    @Test
    void holdingNeverBeatsAHigherPriority() {
        Candidate held = one("plant", Category.PASSIVE, 10, 1f, Effector.RIGHT_ARM);
        Candidate wall = one("wall", Category.PASSIVE, 20, 0f, Effector.RIGHT_ARM);
        Map<Effector, String> holders = new EnumMap<>(Effector.class);
        holders.put(Effector.RIGHT_ARM, "plant");
        assertSame(wall, Arbiter.resolve(List.of(held, wall), NONE, holders).get(Effector.RIGHT_ARM));
    }

    @Test
    void theResultDoesNotDependOnTheOrderCandidatesCameIn() {
        Candidate a = one("a", Category.PASSIVE, 10, 0.5f, Effector.RIGHT_ARM);
        Candidate b = one("b", Category.PASSIVE, 10, 0.5f, Effector.RIGHT_ARM);
        assertEquals(Arbiter.resolve(List.of(a, b), NONE, NOBODY).get(Effector.RIGHT_ARM).source(),
                Arbiter.resolve(List.of(b, a), NONE, NOBODY).get(Effector.RIGHT_ARM).source());
    }

    @Test
    void noCandidatesNoOwners() {
        assertTrue(Arbiter.resolve(List.of(), NONE, NOBODY).isEmpty());
        assertNull(Arbiter.resolve(List.of(one("a", Category.IDLE, 0, 1f, Effector.HEAD)), NONE, NOBODY)
                .get(Effector.RIGHT_ARM));
    }
}
