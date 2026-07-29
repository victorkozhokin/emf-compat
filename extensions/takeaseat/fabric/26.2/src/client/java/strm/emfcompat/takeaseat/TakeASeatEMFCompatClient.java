package strm.emfcompat.takeaseat;

import net.fabricmc.api.ClientModInitializer;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

/**
 * Fabric entry point: registers the addon's tab in the shared settings screen. All rendering
 * work is done by the client-side mixins, which check {@link #isEnabled()}.
 */
public class TakeASeatEMFCompatClient implements ClientModInitializer {

    public static final String MOD_ID = "emf_compat_takeaseat";

    public static final String KEY_ENABLED = "takeaseat.enabled";

    @Override
    public void onInitializeClient() {
        ConfigRegistry.section(MOD_ID, "Take a Seat")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to Take a Seat sitting poses.",
                        "Off", "Disable all EMF compatibility for Take a Seat.");
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }
}
