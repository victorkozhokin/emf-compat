package strm.emfcompat.supplementaries;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(EMFCompatSupplementariesMod.MOD_ID)
public class EMFCompatSupplementariesMod {

    public static final String MOD_ID = "emf_compat_supplementaries";

    public EMFCompatSupplementariesMod(IEventBus modEventBus) {
        // All actual work is done by the client-side mixins.
    }
}
