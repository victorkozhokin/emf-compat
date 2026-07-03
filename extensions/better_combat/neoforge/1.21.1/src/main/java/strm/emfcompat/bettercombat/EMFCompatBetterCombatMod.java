package strm.emfcompat.bettercombat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.utils.EMFEntity;
import strm.emfcompat.bettercombat.compat.AttackPauseOverride;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseManager;

import java.util.UUID;

@Mod(EMFCompatBetterCombatMod.MOD_ID)
public class EMFCompatBetterCombatMod {
    public static final String MOD_ID = "emf_compat_better_combat";
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatBetterCombat");
    private static final String SOURCE = "better_combat";

    public EMFCompatBetterCombatMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                EMFAnimationApi.registerVanillaModelCondition(EMFCompatBetterCombatMod::shouldForceVanillaModelInFirstPerson);
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
        return AttackPauseOverride.isUnpaused(uuid) || PoseManager.getSavedPoses(player, SOURCE) != null;
    }
}
