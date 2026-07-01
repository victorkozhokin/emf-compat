package strm.emfcompat.carryon;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(EMFCarryOnMod.MOD_ID)
public class EMFCarryOnMod {
    public static final String MOD_ID = "emf_compat_carry_on";

    public EMFCarryOnMod(IEventBus modEventBus, ModContainer modContainer) {
        // All actual work is done by the client-side HumanoidModelMixin.
    }
}
