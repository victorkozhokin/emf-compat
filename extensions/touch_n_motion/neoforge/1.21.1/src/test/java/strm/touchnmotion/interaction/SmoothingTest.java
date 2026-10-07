package strm.touchnmotion.interaction;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmoothingTest {

    /** Where a fade from 0 towards 1 is after {@code seconds}, run at {@code fps}. */
    private static float fadeAfter(double seconds, int fps, double tau) {
        double dt = 1.0 / fps;
        float weight = 0f;
        for (int i = 0; i < Math.round(seconds * fps); i++) weight += (1f - weight) * Smoothing.fadeIn(dt, tau);
        return weight;
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 60, 144})
    void theSameCurveAtAnyFrameRate(int fps) {
        // 0.5 s (a whole number of frames at each rate) into a 0.3 s fade: 1 - e^(-0.5/0.3) = 0.811
        assertEquals(1 - Math.exp(-0.5 / 0.3), fadeAfter(0.5, fps, 0.3), 1e-3);
    }

    @Test
    void zeroSecondsFollowsAtOnce() {
        assertEquals(1f, Smoothing.follow(0.016, 0));
    }

    @Test
    void theFirstFrameStartsAFadeInFromNothingAndEndsAFadeOut() {
        assertEquals(0f, Smoothing.fadeIn(0, 0.2));
        assertEquals(1f, Smoothing.fadeOut(0, 0.2));
    }
}
