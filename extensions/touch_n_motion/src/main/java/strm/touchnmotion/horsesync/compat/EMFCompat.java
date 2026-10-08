package strm.touchnmotion.horsesync.compat;

import strm.touchnmotion.interaction.EntityStates;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EMFCompat {
    public static final Map<UUID, Float> horseBodyOffsets = EntityStates.alsoClear(new HashMap<>());
}
