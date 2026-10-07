package strm.emfcompat.core;

import net.minecraft.client.Minecraft;

import java.util.UUID;

/**
 * Shared helpers for the EMF compatibility framework.
 */
public final class EMFCompatCore {

    /**
     * Option id of the global switch. Lives in the core's own config section; when it is off,
     * every addon's own {@code <addon>.enabled} option reads as off too (see
     * {@code EMFCompatConfig#getBoolean}), and the core stops handing out saved poses.
     */
    public static final String KEY_COMPAT_ENABLED = "core.enabled";

    private static volatile boolean compatEnabled = true;

    private EMFCompatCore() {
    }

    /**
     * Whether EMF compatibility is enabled at all. {@code false} turns the whole framework
     * inert: addons report themselves as disabled and no captured pose is ever restored.
     */
    public static boolean isCompatEnabled() {
        return compatEnabled;
    }

    /**
     * Sets the global switch. Called by the config on load and whenever the option is toggled;
     * on loaders without a config screen it simply stays at its default of {@code true}.
     */
    public static void setCompatEnabled(boolean enabled) {
        compatEnabled = enabled;
    }

    /**
     * Returns {@code true} if the entity with the given UUID is the local player and the camera
     * is currently in first person. This is used by core mixins to skip pose restoration for
     * the first-person view.
     */
    public static boolean isLocalPlayerInFirstPerson(UUID uuid) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (!mc.player.getUUID().equals(uuid)) return false;
        if (!mc.options.getCameraType().isFirstPerson()) return false;
        if (mc.getCameraEntity() != mc.player) return false;
        // First Person Model renders the local body in first person, so treat it as not
        // first-person for the purposes of pose capture/restoration.
        return !FirstPersonModelCompat.isActive();
    }
}
