package strm.emfcompat.quark;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(EMFCompatQuarkMod.MOD_ID)
public class EMFCompatQuarkMod {

    public static final String MOD_ID = "emf_compat_quark";

    public EMFCompatQuarkMod(IEventBus modEventBus) {
        // All actual work is done by the client-side mixins.
    }
}
