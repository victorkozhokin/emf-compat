package strm.emfcompat.create;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(EMFCompatCreateMod.MOD_ID)
public class EMFCompatCreateMod {

    public static final String MOD_ID = "emf_compat_create";

    public EMFCompatCreateMod(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("entity_model_features") && ModList.get().isLoaded("emf_compat_core")) {
            CreateCompat.init();
        }
    }
}
