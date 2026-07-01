package strm.neaemfcompat;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import strm.neaemfcompat.compat.EMFCompat;

@Mod(NEAEMFCompat.MOD_ID)
public class NEAEMFCompat {

    public static final String MOD_ID = "emf_compat_not_enough_animations";

    public NEAEMFCompat() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("entity_model_features")) {
            EMFCompat.init();
        }
    }

}
