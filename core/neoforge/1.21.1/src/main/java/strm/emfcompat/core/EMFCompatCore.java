package strm.emfcompat.core;

import net.minecraft.client.Minecraft;

import java.util.UUID;

/**
 * Shared helpers for the EMF compatibility framework.
 */
public final class EMFCompatCore {

    private EMFCompatCore() {
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
