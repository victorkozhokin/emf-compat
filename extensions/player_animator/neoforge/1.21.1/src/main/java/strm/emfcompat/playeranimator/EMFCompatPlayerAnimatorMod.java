package strm.emfcompat.playeranimator;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(EMFCompatPlayerAnimatorMod.MOD_ID)
public class EMFCompatPlayerAnimatorMod {
    public static final String MOD_ID = "emf_compat_player_animator";

    public EMFCompatPlayerAnimatorMod(IEventBus modEventBus, ModContainer modContainer) {
        // All actual work is done by the client-side PlayerModelMixin.
    }
}
