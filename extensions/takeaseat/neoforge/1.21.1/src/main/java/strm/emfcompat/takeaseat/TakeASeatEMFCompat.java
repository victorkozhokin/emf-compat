package strm.emfcompat.takeaseat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import strm.emfcompat.takeaseat.compat.EMFCompat;

@Mod(TakeASeatEMFCompat.MOD_ID)
public class TakeASeatEMFCompat {

    public static final String MOD_ID = "emf_compat_takeaseat";

    public TakeASeatEMFCompat(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        EMFCompat.init();
    }
}
