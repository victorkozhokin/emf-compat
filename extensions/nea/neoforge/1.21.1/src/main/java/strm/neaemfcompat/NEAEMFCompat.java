package strm.neaemfcompat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.neaemfcompat.compat.EMFCompat;

@Mod(NEAEMFCompat.MOD_ID)
public class NEAEMFCompat {

    public static final String MOD_ID = "emf_compat_not_enough_animations";
    public static final String KEY_ENABLED = "nea.enabled";

    public NEAEMFCompat(IEventBus modEventBus) {
        ConfigRegistry.section(MOD_ID, "Not Enough Animations")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to NotEnoughAnimations poses.",
                        "Off", "Disable all EMF compatibility for NotEnoughAnimations.");
        modEventBus.addListener(this::onClientSetup);
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("entity_model_features")) {
            EMFCompat.init();
        }
    }

}
