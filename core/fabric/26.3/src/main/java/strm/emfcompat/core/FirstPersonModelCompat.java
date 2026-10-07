package strm.emfcompat.core;

import java.lang.reflect.Method;

/**
 * Optional compatibility with the First Person Model mod.
 * When FPM is installed and enabled, the local player body is rendered in first person,
 * so the core should not skip pose capture/restoration.
 */
public final class FirstPersonModelCompat {

    private static final String API_CLASS = "dev.tr7zw.firstperson.api.FirstPersonAPI";
    private static final String IS_ENABLED_METHOD = "isEnabled";

    private FirstPersonModelCompat() {
    }

    /**
     * Holds the resolved API method, or {@code null} when First Person Model is absent.
     *
     * <p>The lookup must happen exactly once. {@link #isActive()} sits on a per-model-part render
     * path — EMF calls {@code EMFModelPartRoot.animate()} from every part's {@code render}, so this
     * runs a dozen times per player per frame. A failed {@code Class.forName} is not cached by the
     * JVM: each call walks the whole classpath and builds a {@link ClassNotFoundException} with a
     * stack trace, which on a large modpack costs a measurable share of the render thread. Holding
     * the result in a class-init'd field makes the miss cost exactly one lookup.</p>
     *
     * <p>Resolved lazily through a holder class rather than a static block so the JVM's class
     * initialisation gives us the thread safety for free, and the lookup happens on first render
     * rather than at mod construction — by which point every mod's classes are available.</p>
     */
    private static final class Api {
        static final Method IS_ENABLED = resolve();

        private static Method resolve() {
            try {
                return Class.forName(API_CLASS).getMethod(IS_ENABLED_METHOD);
            } catch (Throwable t) {
                return null;
            }
        }
    }

    public static boolean isActive() {
        Method isEnabled = Api.IS_ENABLED;
        // First Person Model is not installed — the common case, and now a single field read.
        if (isEnabled == null) {
            return false;
        }
        try {
            // The mod can be toggled in game, so the value itself must be read every time.
            return isEnabled.invoke(null) instanceof Boolean enabled && enabled;
        } catch (Throwable t) {
            return false;
        }
    }
}
