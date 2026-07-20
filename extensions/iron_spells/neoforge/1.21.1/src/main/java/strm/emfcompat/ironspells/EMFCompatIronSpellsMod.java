package strm.emfcompat.ironspells;

import io.redspace.ironsspellbooks.config.ClientConfigs;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.utils.EMFEntity;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.ironspells.compat.IronSpellsCompat;

import java.util.UUID;

@Mod(EMFCompatIronSpellsMod.MOD_ID)
public class EMFCompatIronSpellsMod {
    public static final String MOD_ID = "emf_compat_iron_spells";
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatIronSpells");

    public EMFCompatIronSpellsMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                EMFAnimationApi.registerVanillaModelCondition(EMFCompatIronSpellsMod::shouldForceVanillaModelInFirstPerson);
                LOGGER.info("Registered EMF first-person vanilla-model condition for Iron's Spells");
            } catch (Exception e) {
                LOGGER.error("Failed to register EMF vanilla-model condition", e);
            }
        });
    }

    /**
     * Forces the vanilla player model in first person while casting a spell, but only when
     * Iron's Spells is configured to show the third-person arms/items in first person.
     * Without this, EMF's custom first-person model can hide the casting animation arms.
     */
    private static boolean shouldForceVanillaModelInFirstPerson(EMFEntity entity) {
        Entity mcEntity = (Entity) entity;
        if (!(mcEntity instanceof AbstractClientPlayer player)) {
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
        if (!IronSpellsCompat.isCasting(player)) {
            return false;
        }
        return ClientConfigs.SHOW_FIRST_PERSON_ARMS.get() || ClientConfigs.SHOW_FIRST_PERSON_ITEMS.get();
    }
}
