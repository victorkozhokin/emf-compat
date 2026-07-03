package strm.emfcompat.bettercombat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import traben.entity_model_features.utils.EMFEntity;
import strm.emfcompat.bettercombat.compat.AttackPauseOverride;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;

import java.util.UUID;

/**
 * Fabric client entry point for the Better Combat EMF compatibility addon.
 */
public class EMFCompatBetterCombatClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatBetterCombat");
    private static final String SOURCE = "better_combat";
    private static boolean cleaned = false;

    @Override
    public void onInitializeClient() {
        // EMF registers its PAL pause listener in its own mod initializer. Because Fabric does not
        // guarantee the relative order of our initializer and EMF's, the listener may not be present
        // when this method runs. Defer cleanup to the first client tick, by which point all mod
        // initializers have definitely completed.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (cleaned) return;
            cleaned = true;

            int before = EMFAnimationEntityContext.pauseListeners.size();
            EMFAnimationEntityContext.pauseListeners.forEach(listener ->
                    LOGGER.info("EMF pause listener found: {}", listener.getClass().getName()));
            boolean removed = EMFAnimationEntityContext.pauseListeners.removeIf(listener -> {
                String name = listener.getClass().getName();
                // EMF registers PALCompat either as a lambda generated inside the EMF class or
                // as a method-reference class from PALCompat itself. Remove any internal EMF listener
                // so we can manage the Better Combat conflict ourselves.
                boolean match = name.startsWith("traben.entity_model_features.") || name.contains("PALCompat");
                if (match) {
                    LOGGER.info("Removing EMF PAL pause listener: {}", name);
                }
                return match;
            });
            int after = EMFAnimationEntityContext.pauseListeners.size();
            LOGGER.info("EMF pause listener cleanup: before={}, after={}, removed={}", before, after, removed);

            // Force EMF to use the vanilla player model for the local player in first person while a
            // Better Combat attack is active. This prevents EMF player animation resource packs from
            // overriding the first-person arm pose and breaking the held item position.
            try {
                EMFAnimationApi.registerVanillaModelCondition(EMFCompatBetterCombatClient::shouldForceVanillaModelInFirstPerson);
                LOGGER.info("Registered EMF first-person vanilla-model condition for Better Combat");
            } catch (Exception e) {
                LOGGER.error("Failed to register EMF vanilla-model condition", e);
            }
        });
    }

    private static boolean shouldForceVanillaModelInFirstPerson(EMFEntity entity) {
        Entity mcEntity = (Entity) entity;
        if (!(mcEntity instanceof Player player)) {
            return false;
        }
        if (!player.isLocalPlayer()) {
            return false;
        }
        UUID uuid = player.getUUID();
        if (uuid == null) {
            return false;
        }
        if (!EMFCompatCore.isLocalPlayerInFirstPerson(uuid)) {
            return false;
        }
        return AttackPauseOverride.isUnpaused(uuid) || PoseManager.getSavedPoses(uuid, SOURCE) != null;
    }
}