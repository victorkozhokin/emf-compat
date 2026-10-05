package strm.emfcompat.animationadditions;

import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

/**
 * The one line each feature logs when what it decided changes ("stride", "hover-R", "brace"): what
 * the mctest baselines compare. Off unless asked for - with every player and horse in view writing
 * theirs it is only noise in a player's log; mctest turns it on in its sandbox.
 */
public final class DecisionLog {

    public static final String KEY_ENABLED = "debug.decisions";

    private DecisionLog() {
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Log decisions (debug)", false,
                "On", "Log what each feature decides for each player whenever it changes - for debugging and the test baselines.",
                "Off", "Keep the log quiet.");
    }

    public static boolean isOn() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, false);
    }
}
