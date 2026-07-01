package strm.emfcompat.corpse;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(EMFCorpseMod.MOD_ID)
public class EMFCorpseMod {

    public static final String MOD_ID = "emf_compat_corpse";

    public EMFCorpseMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("entity_model_features") && ModList.get().isLoaded("corpse")) {
            CorpseTracker.init();
        }
    }
}
