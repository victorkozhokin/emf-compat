package strm.emfcompat.animationadditions.interaction;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EntityStatesTest {

    private static final class Counter {
        int n;
    }

    @Test
    void exclusiveTakeoverDropsOnlyThisPlayersOldPose() {
        EntityStates<Counter> states = new EntityStates<>(Counter::new);
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        long now = System.nanoTime();
        states.seen(player, now).value.n = 9;
        Counter unaffected = states.seen(other, now).value;
        states.forget(player);
        assertNull(states.fresh(player));
        assertSame(unaffected, states.fresh(other));
        assertEquals(0, states.seen(player, now).value.n);
    }

    @Test
    void solvesOnFirstSightThenNotBeforeTheInterval() {
        EntityStates<Counter> states = new EntityStates<>(Counter::new);
        UUID id = UUID.randomUUID();
        long t = 1_000_000_000L;
        EntityStates.Entry<Counter> entry = states.seen(id, t);
        assertEquals(0.0, EntityStates.due(entry, t), "first solve has no time step");
        assertEquals(-1.0, EntityStates.due(states.seen(id, t + 1_000_000L), t + 1_000_000L), "1 ms later: skip");
        double dt = EntityStates.due(states.seen(id, t + 20_000_000L), t + 20_000_000L);
        assertEquals(0.02, dt, 1e-9);
    }

    @Test
    void aLongPauseIsCappedSoNothingJumps() {
        EntityStates<Counter> states = new EntityStates<>(Counter::new);
        UUID id = UUID.randomUUID();
        EntityStates.Entry<Counter> entry = states.seen(id, 1L);
        EntityStates.due(entry, 1L);
        assertEquals(0.1, EntityStates.due(states.seen(id, 3_000_000_000L), 3_000_000_000L), 1e-9);
    }

    @Test
    void theSameEntityKeepsItsStateAndClearAllDropsIt() {
        EntityStates<Counter> states = new EntityStates<>(Counter::new);
        UUID id = UUID.randomUUID();
        long now = System.nanoTime();
        Counter first = states.seen(id, now).value;
        assertSame(first, states.seen(id, now).value);
        assertNotNull(states.fresh(id));
        EntityStates.clearAll();
        assertNull(states.fresh(id));
        assertEquals(0, states.size());
    }

    @Test
    void tooManyAtOnceDropTheOnesSeenLongestAgoAndKeepTheOneDrawnNow() {
        EntityStates<Counter> states = new EntityStates<>(Counter::new);
        long t = System.nanoTime();
        UUID first = UUID.randomUUID();
        Counter mine = states.seen(first, t).value;
        for (int i = 1; i <= 400; i++) {
            states.seen(UUID.randomUUID(), t + i);
            // The player is drawn every frame, among however many others.
            assertSame(mine, states.seen(first, t + i).value, "after " + i + " others");
        }
        assertEquals(256, states.size(), 1);
    }
}
