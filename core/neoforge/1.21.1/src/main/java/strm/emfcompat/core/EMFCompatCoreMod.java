package strm.emfcompat.core;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(EMFCompatCoreMod.MOD_ID)
public class EMFCompatCoreMod {

    public static final String MOD_ID = "emf_compat_core";

    public EMFCompatCoreMod(IEventBus modEventBus) {
        // Core initializes itself via static helpers; addons call PoseManager directly.
    }
}
