package strm.emfcompat.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * Shared helpers for the EMF compatibility framework.
 */
public final class EMFCompatCore {

    private static final String CORPSE_DUMMY_CLASS_PREFIX = "de.maxhenkel.corpse.entities.Dummy";

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

    /**
     * Returns {@code true} if the given entity is a Corpse mod dummy player/skeleton.
     * Corpse renders dead bodies by creating a dummy entity that reuses the deceased player's
     * UUID; those dummies must not receive pose overrides from any extension.
     */
    public static boolean isCorpseDummy(Entity entity) {
        if (entity == null) {
            return false;
        }
        return entity.getClass().getName().startsWith(CORPSE_DUMMY_CLASS_PREFIX);
    }
}
