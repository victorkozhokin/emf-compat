package strm.emfcompat.hackersandslashers.compat;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Hands the attack's turn of the torso to EMF Compat: Animation Additions, when that addon is
 * installed - found once, by name, so neither addon needs the other to load.
 */
public final class BodyBridge {
    private static boolean resolved;
    private static Method offer;

    private BodyBridge() {
    }

    /** {@code true} if Animation Additions took the body and the feet on for this frame. */
    public static boolean offer(UUID uuid, float pitch, float yaw, float roll, boolean planted) {
        if (!resolved) {
            resolved = true;
            try {
                offer = Class.forName("strm.emfcompat.animationadditions.compat.CombatBody")
                        .getMethod("offer", UUID.class, float.class, float.class, float.class, boolean.class);
            } catch (Throwable absent) {
                offer = null;
            }
        }
        if (offer == null) return false;
        try {
            return (Boolean) offer.invoke(null, uuid, pitch, yaw, roll, planted);
        } catch (Throwable t) {
            // Nothing may escape a render-time call (see HnSCompat).
            return false;
        }
    }
}
