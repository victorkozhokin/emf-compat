package strm.emfcompat.core;

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

    public static boolean isActive() {
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Object result = apiClass.getMethod(IS_ENABLED_METHOD).invoke(null);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            return false;
        }
    }
}
