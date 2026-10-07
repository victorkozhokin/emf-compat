package strm.touchnmotion;

import net.fabricmc.api.ModInitializer;

/**
 * Touch'n Motion on Fabric: the part that runs on both sides - the channels a server passes
 * players' hands on by. Everything else is the client's ({@link TouchNMotionClient}).
 */
public class TouchNMotionMod implements ModInitializer {

    public static final String MOD_ID = "touch_n_motion";
    /** The mod's master option; the core's global switch covers it by its suffix. */
    public static final String KEY_ENABLED = "touchnmotion.enabled";

    @Override
    public void onInitialize() {
        strm.touchnmotion.net.HandsNet.register();
    }
}
