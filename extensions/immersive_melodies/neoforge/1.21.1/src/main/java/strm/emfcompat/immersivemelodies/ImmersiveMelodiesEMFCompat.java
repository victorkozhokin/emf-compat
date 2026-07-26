package strm.emfcompat.immersivemelodies;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

@Mod(ImmersiveMelodiesEMFCompat.MOD_ID)
public class ImmersiveMelodiesEMFCompat {

    public static final String MOD_ID = "emf_compat_immersive_melodies";
    public static final String KEY_ENABLED = "immersivemelodies.enabled";

    public ImmersiveMelodiesEMFCompat(IEventBus modEventBus) {
        ConfigRegistry.section(MOD_ID, "Immersive Melodies")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to Immersive Melodies instrument poses.",
                        "Off", "Disable all EMF compatibility for Immersive Melodies.");
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }
}
