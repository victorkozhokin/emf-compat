package strm.emfcompat.horsesync;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import strm.emfcompat.horsesync.compat.EMFCompat;

@Mod(EMFHorseSync.MOD_ID)
public class EMFHorseSync {

    public static final String MOD_ID = "emf_compat_horse_sync";

    public EMFHorseSync(IEventBus modEventBus, ModContainer modContainer) {
        //modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        EMFCompat.init();
    }
}
