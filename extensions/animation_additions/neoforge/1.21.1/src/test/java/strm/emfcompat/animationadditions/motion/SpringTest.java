package strm.emfcompat.animationadditions.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringTest {

    private static Spring run(int fps, double seconds, float from, float target, double halflife) {
        Spring spring = new Spring();
        spring.set(from);
        for (int i = 0; i < Math.round(fps * seconds); i++) spring.update(target, halflife, 1.0 / fps);
        return spring;
    }

    @Test
    void movesTheSameAtAnyFrameRate() {
        Spring slow = run(30, 0.2, 0f, 1f, 0.1), fast = run(240, 0.2, 0f, 1f, 0.1);
        assertEquals(slow.value, fast.value, 1e-4f);
        assertEquals(slow.velocity, fast.velocity, 1e-3f);
    }

    @Test
    void settlesOnTheTargetWithoutGoingPastIt() {
        Spring spring = new Spring();
        float last = 0f;
        for (int i = 0; i < 240; i++) {
            spring.update(1f, 0.08, 1.0 / 120);
            assertTrue(spring.value >= last - 1e-6f && spring.value <= 1f + 1e-6f, "frame " + i);
            last = spring.value;
        }
        assertEquals(1f, spring.value, 1e-4f);
        assertEquals(0f, spring.velocity, 1e-3f);
    }

    @Test
    void anOffsetWithSpeedCarriesOnBeforeItComesBack() {
        // What pose inertia does at a cut: an offset that keeps going the way the limb went.
        Spring spring = new Spring();
        spring.value = 0.5f;
        spring.velocity = 4f;
        spring.update(0f, 0.09, 1.0 / 60);
        assertTrue(spring.value > 0.5f);
        for (int i = 0; i < 120; i++) spring.update(0f, 0.09, 1.0 / 60);
        assertEquals(0f, spring.value, 1e-4f);
    }

    @Test
    void noTimeIsNoMoveAndNoHalflifeIsAtOnce() {
        Spring spring = new Spring();
        spring.set(0.3f);
        spring.update(1f, 0.1, 0);
        assertEquals(0.3f, spring.value);
        spring.update(1f, 0, 1.0 / 60);
        assertEquals(1f, spring.value);
        assertEquals(0f, spring.velocity);
    }
}
