package strm.emfcompat.bettercombat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(EMFCompatBetterCombatMod.MOD_ID)
public class EMFCompatBetterCombatMod {
    public static final String MOD_ID = "emf_compat_better_combat";

    public EMFCompatBetterCombatMod(IEventBus modEventBus, ModContainer modContainer) {
        // All actual work is done by the client-side PlayerModelMixin.
    }
}
