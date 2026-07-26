package strm.emfcompat.gliders;

import net.minecraftforge.fml.common.Mod;
import strm.emfcompat.gliders.compat.GlidingState;

@Mod(EMFCompatGlidersMod.MOD_ID)
public class EMFCompatGlidersMod {
    public static final String MOD_ID = "emf_compat_gliders";

    public EMFCompatGlidersMod() {
        // Soft requirement: at least one supported glider mod must be present.
        // Throwing here makes the loader show its mod loading error screen.
        if (!GlidingState.anyGliderModLoaded()) {
            throw new IllegalStateException(
                    "EMF Compat: Gliders requires at least one supported glider mod to be installed: "
                            + "Paragliders (paraglider) or Gliders (vc_gliders).");
        }
        // All actual work is done by the client-side HumanoidModelMixin.
    }
}
