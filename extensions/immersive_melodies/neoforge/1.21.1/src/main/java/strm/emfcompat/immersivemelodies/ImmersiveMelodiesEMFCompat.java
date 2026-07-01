package strm.emfcompat.immersivemelodies;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(ImmersiveMelodiesEMFCompat.MOD_ID)
public class ImmersiveMelodiesEMFCompat {

    public static final String MOD_ID = "emf_compat_immersive_melodies";

    public ImmersiveMelodiesEMFCompat(IEventBus modEventBus) {
        // All actual work is done by the client-side mixins.
    }
}
