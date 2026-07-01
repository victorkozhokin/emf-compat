package strm.emfcompat.horsesync.compat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EMFCompat {
    public static final Map<UUID, Float> horseBodyOffsets = new HashMap<>();

    public static void init() {
        // No API registration is required: the horse body offset is captured directly
        // from EMF's animated model parts via a mixin.
    }
}
