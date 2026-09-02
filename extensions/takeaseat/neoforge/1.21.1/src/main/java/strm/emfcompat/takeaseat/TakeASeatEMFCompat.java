package strm.emfcompat.takeaseat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.takeaseat.compat.EMFCompat;

@Mod(TakeASeatEMFCompat.MOD_ID)
public class TakeASeatEMFCompat {

    public static final String MOD_ID = "emf_compat_takeaseat";
    public static final String KEY_ENABLED = "takeaseat.enabled";

    public TakeASeatEMFCompat(IEventBus modEventBus) {
        ConfigRegistry.section(MOD_ID, "Take a Seat")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to Take a Seat sitting poses.",
                        "Off", "Disable all EMF compatibility for Take a Seat.");
        modEventBus.addListener(this::onClientSetup);
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        EMFCompat.init();
        // EMF calls these back around each entity's animation pass.
        TakeASeatAnimationHook.register();
    }
}
