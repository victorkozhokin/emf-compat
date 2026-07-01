package strm.emfcompat.corpse;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(EMFCorpseMod.MOD_ID)
public class EMFCorpseMod {

    public static final String MOD_ID = "emf_compat_corpse";

    public EMFCorpseMod(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("entity_model_features") && ModList.get().isLoaded("corpse")) {
            CorpseTracker.init();
        }
    }
}
