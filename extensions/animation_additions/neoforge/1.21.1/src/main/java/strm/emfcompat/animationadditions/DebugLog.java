package strm.emfcompat.animationadditions;

import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

/**
 * What the features write to the log for debugging, both off unless asked for.
 *
 * <p>{@link #decisions}: the one line each feature logs when what it decided changes ("stride",
 * "hover-R", "brace") - what the mctest baselines compare. With every player and horse in view
 * writing theirs it is only noise in a player's log; mctest turns it on in its sandbox.</p>
 *
 * <p>{@link #trace}: the numbers behind them, every frame or ten times a second - the feet, the
 * motion, the pose cuts, the hand's distance. It floods the log.</p>
 */
public final class DebugLog {

    public static final String KEY_DECISIONS = "debug.decisions";
    /** Named for the feet, which had it first; the scripts that turn it on know it by this. */
    public static final String KEY_TRACE = "footgrounding.trace";

    private DebugLog() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_DECISIONS, "Log decisions", false,
                        "On", "Log what each feature decides for each player whenever it changes - for debugging and the test baselines.",
                        "Off", "Keep the log quiet.")
                .addBoolean(KEY_TRACE, "Trace feet, motion and hands", false,
                        "On", "Log what the feet, the motion and the hands measure and do, every frame they do anything - for debugging only, it floods the log.",
                        "Off", "No per-frame numbers in the log.");
    }

    public static boolean decisions() {
        return EMFCompatConfig.getBoolean(KEY_DECISIONS, false);
    }

    public static boolean trace() {
        return EMFCompatConfig.getBoolean(KEY_TRACE, false);
    }
}
