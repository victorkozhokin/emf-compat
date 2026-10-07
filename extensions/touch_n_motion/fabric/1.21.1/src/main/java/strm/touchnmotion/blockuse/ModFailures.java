package strm.touchnmotion.blockuse;

import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

/**
 * What to do when a call into an optional mod fails. A mod that is not there, or whose classes are
 * not what was expected, will not get better: that is given up on for good. Anything else - a
 * block entity gone mid-frame, a contraption half unloaded - is that one call's trouble: it is
 * skipped and the next one tried, and only a failure that keeps coming is given up on.
 *
 * <p>The first failure is logged, with its cause; a mod simply not installed is not one.</p>
 */
public final class ModFailures {

    private static final int GIVE_UP_AFTER = 100;

    private final String what;
    private int count;
    private boolean logged, off;

    /** @param what what could not be done, for the log: "read the train controls" */
    public ModFailures(String what) {
        this.what = what;
    }

    /** Whether this was given up on. */
    public boolean off() {
        return off;
    }

    public void failed(Throwable t) {
        Throwable cause = t instanceof InvocationTargetException && t.getCause() != null ? t.getCause() : t;
        boolean forGood = t instanceof ClassNotFoundException || t instanceof NoSuchMethodException
                || t instanceof NoSuchFieldException || t instanceof IllegalAccessException
                || cause instanceof LinkageError;
        if (forGood || ++count >= GIVE_UP_AFTER) off = true;
        if (!logged && !(t instanceof ClassNotFoundException)) {
            logged = true;
            LoggerFactory.getLogger("TouchNMotion").warn("[TouchNMotion] could not {}{}",
                    what, forGood ? "; left out from now on" : "; skipped this once", t);
        }
    }
}
