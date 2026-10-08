package strm.neaemfcompat;

import net.fabricmc.api.ClientModInitializer;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.neaemfcompat.compat.EMFCompat;

public class NEAEMFCompatClient implements ClientModInitializer {

    public static final String MOD_ID = "emf_compat_not_enough_animations";

    public static final String KEY_ENABLED = "nea.enabled";

    @Override
    public void onInitializeClient() {
        ConfigRegistry.section(MOD_ID, "Not Enough Animations")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to NotEnoughAnimations poses.",
                        "Off", "Disable all EMF compatibility for NotEnoughAnimations.");

        EMFCompat.init();
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }
}
