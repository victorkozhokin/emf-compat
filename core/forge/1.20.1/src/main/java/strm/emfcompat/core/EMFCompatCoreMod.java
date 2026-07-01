package strm.emfcompat.core;

import net.minecraftforge.fml.common.Mod;

@Mod(EMFCompatCoreMod.MOD_ID)
public class EMFCompatCoreMod {

    public static final String MOD_ID = "emf_compat_core";

    public EMFCompatCoreMod() {
        // Core initializes itself via static helpers; addons call PoseManager directly.
    }
}
