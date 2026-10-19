package strm.touchnmotion.horsesync.compat;

import strm.touchnmotion.interaction.EntityStates;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EMFCompat {
    /**
     * Where a resource pack has the horse's body this frame, in its model's pixels: the pivot as the
     * model has it ({@code y0}, {@code z0}) and as the pack has moved it ({@code y}, {@code z}), the turn
     * about its side ({@code pitch}, nose down positive) and the renderer's scale.
     */
    public record Saddle(float y0, float z0, float y, float z, float pitch, float scale) {
        /** Blocks the body is lifted in the world. */
        public float lift() {
            return (y0 - y) / 16f * scale;
        }
    }

    public static final Map<UUID, Saddle> horseSaddles = EntityStates.alsoClear(new HashMap<>());
}
