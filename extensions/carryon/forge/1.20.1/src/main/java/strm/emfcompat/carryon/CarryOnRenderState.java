package strm.emfcompat.carryon;

import net.minecraft.world.entity.Entity;
import strm.emfcompat.core.BodyPartSync;
import traben.entity_model_features.EMFAnimationApi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks Carry On render state that needs to be shared across mixins.
 *
 * <p>Body-part pose deltas are delegated to {@link BodyPartSync} in the core
 * module. This class only keeps Carry-On-specific state: the set of entities
 * being carried this frame, used by the registered EMF vanilla-model condition.</p>
 */
public final class CarryOnRenderState {

    private CarryOnRenderState() {
    }

    // Entities currently being rendered as carried by Carry On.
    // EMF uses this set via the registered vanilla-model condition.
    private static final Set<UUID> CARRIED_ENTITIES = Collections.newSetFromMap(new HashMap<>());

    static {
        try {
            EMFAnimationApi.registerVanillaModelCondition(emfEntity -> {
                UUID uuid = emfEntity.etf$getUuid();
                return uuid != null && CARRIED_ENTITIES.contains(uuid);
            });
        } catch (Exception ignored) {
            // EMF is a required dependency, so this should not happen.
        }
    }

    /**
     * Marks the given entity as being carried this frame.
     * EMF's registered vanilla-model condition will pick this up and force the
     * vanilla model for the duration of the render pass.
     */
    public static void markCarried(Entity entity) {
        if (entity == null) return;
        UUID uuid = entity.getUUID();
        if (uuid != null) {
            CARRIED_ENTITIES.add(uuid);
        }
    }

    /**
     * Clears the carried-entity tracking set at the start of each Carry On
     * third-person render pass.
     */
    public static void clearCarriedEntities() {
        CARRIED_ENTITIES.clear();
    }

    /**
     * Removes all tracked data for the given player UUID.
     */
    public static void clear(UUID uuid) {
        BodyPartSync.clear(uuid);
    }
}
