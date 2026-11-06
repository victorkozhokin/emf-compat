package strm.touchnmotion.net;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LatestStatesTest {
    @Test void aBurstKeepsReleaseAndDoesNotSpendAnEventsBudget() {
        var states = new LatestStates<String, String>();
        List<String> delivered = new ArrayList<>();
        for (int i = 0; i < 100; i++) states.put("player", "held-" + i);
        states.flush("player", (who, state) -> delivered.add(state));
        delivered.add("seed");
        states.put("player", "released");
        states.flush((who, state) -> delivered.add(state));
        assertEquals(List.of("held-99", "seed", "released"), delivered);
        states.flush((who, state) -> fail("already sent"));
    }

    @Test void anEventFlushesOnlyItsSendersInput() {
        var states = new LatestStates<String, String>();
        List<String> delivered = new ArrayList<>();
        states.put("A", "wheel");
        states.put("B", "typing");
        states.flush("B", (who, state) -> delivered.add(who + ":" + state));
        states.flush((who, state) -> delivered.add(who + ":" + state));
        assertEquals(List.of("B:typing", "A:wheel"), delivered);
    }
}
