package strm.emfcompat.emotecraft;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(EMFCompatEmotecraftMod.MOD_ID)
public class EMFCompatEmotecraftMod {
    public static final String MOD_ID = "emf_compat_emotecraft";

    public EMFCompatEmotecraftMod(IEventBus modEventBus, ModContainer modContainer) {
        // All actual work is done by the client-side PlayerModelMixin.
    }
}
