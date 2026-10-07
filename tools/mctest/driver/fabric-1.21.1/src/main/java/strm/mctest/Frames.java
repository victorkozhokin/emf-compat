package strm.mctest;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import java.util.Arrays;

/**
 * Frame times for performance runs: {@code {"frames": "start"}} begins recording every frame's
 * length, {@code {"frames": "report"}} stops and gives the frames counted, the seconds they took,
 * the mean frames a second, the median frame and the slowest hundredth and thousandth, in
 * milliseconds. Take the frame cap off first ({@code {"maxFps": 260}} - 260 is unlimited).
 */
final class Frames {

    private static long[] times = new long[1 << 16];
    private static int count;
    private static long last;
    private static boolean recording, listening;

    private Frames() {
    }

    static void start() {
        if (!listening) {
            listening = true;
            WorldRenderEvents.END.register(context -> {
                if (!recording) return;
                long now = System.nanoTime();
                if (last != 0) {
                    if (count == times.length) times = Arrays.copyOf(times, count * 2);
                    times[count++] = now - last;
                }
                last = now;
            });
        }
        count = 0;
        last = 0;
        recording = true;
    }

    static JsonObject report() {
        recording = false;
        JsonObject out = new JsonObject();
        out.addProperty("frames", count);
        if (count == 0) return out;
        long[] sorted = Arrays.copyOf(times, count);
        Arrays.sort(sorted);
        long total = 0;
        for (long t : sorted) total += t;
        out.addProperty("seconds", round(total / 1e9));
        out.addProperty("fps", round(count / (total / 1e9)));
        out.addProperty("medianMs", round(sorted[count / 2] / 1e6));
        out.addProperty("p99Ms", round(sorted[(int) Math.min(count - 1, Math.round(count * 0.99))] / 1e6));
        out.addProperty("p999Ms", round(sorted[(int) Math.min(count - 1, Math.round(count * 0.999))] / 1e6));
        out.addProperty("fpsLow1", round(1e9 / sorted[(int) Math.min(count - 1, Math.round(count * 0.99))]));
        return out;
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
