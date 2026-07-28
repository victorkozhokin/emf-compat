package strm.neaemfcompat;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.neaemfcompat.compat.EMFCompat;

@Mod(NEAEMFCompat.MOD_ID)
public class NEAEMFCompat {

    public static final String MOD_ID = "emf_compat_not_enough_animations";

    public static final String KEY_ENABLED = "nea.enabled";

    public NEAEMFCompat() {
        ConfigRegistry.section(MOD_ID, "Not Enough Animations")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to NotEnoughAnimations poses.",
                        "Off", "Disable all EMF compatibility for NotEnoughAnimations.");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
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
