package strm.emfcompat.gliders;

import net.minecraftforge.fml.common.Mod;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.gliders.compat.GlidingState;

@Mod(GlidersEMFCompat.MOD_ID)
public class GlidersEMFCompat {
    public static final String MOD_ID = "emf_compat_gliders";

    public static final String KEY_ENABLED = "gliders.enabled";
    public static final String KEY_BODY_FOLLOW_ARMS = "gliders.bodyFollowArms";
    public static final String KEY_PARAGLIDER = "gliders.paraglider";
    public static final String KEY_VC_GLIDERS = "gliders.vcGliders";

    public GlidersEMFCompat() {
        // Soft requirement: at least one supported glider mod must be present.
        if (!GlidingState.anyGliderModLoaded()) {
            throw new IllegalStateException(
                    "EMF Compat: Gliders requires at least one supported glider mod to be installed: "
                            + "Paragliders (paraglider) or Gliders (vc_gliders).");
        }
        registerConfig();
    }

    private void registerConfig() {
        ConfigRegistry.Section section = ConfigRegistry.section(MOD_ID, "Gliders")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to gliders (arm poses + flight animation).",
                        "Off", "Disable all EMF compatibility for gliders.")
                .addBoolean(KEY_BODY_FOLLOW_ARMS, "Arm sync", true,
                        "Body-follow (new)",
                        "Gliding arm poses keep their shape and follow the moving torso.",
                        "Rotation-only (legacy)",
                        "Gliding arm poses keep only their rotation.");
        if (GlidingState.isParaglidersLoaded()) {
            section.addBoolean(KEY_PARAGLIDER, "Paragliders", true,
                    "On", "Hold the paragliding pose, and play your pack's flight animation while gliding.",
                    "Off", "Leave paragliding to EMF.");
        }
        if (GlidingState.isVcGlidersLoaded()) {
            section.addBoolean(KEY_VC_GLIDERS, "Gliders (vc)", true,
                    "On", "Play the glider's opening animation in full, then hold the gliding pose "
                            + "and your pack's flight animation.",
                    "Off", "Leave VC gliding to EMF.");
        }
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isBodyFollow() {
        return EMFCompatConfig.getBoolean(KEY_BODY_FOLLOW_ARMS, true);
    }

    public static boolean isParagliderEnabled() {
        return EMFCompatConfig.getBoolean(KEY_PARAGLIDER, true);
    }

    public static boolean isVcGlidersEnabled() {
        return EMFCompatConfig.getBoolean(KEY_VC_GLIDERS, true);
    }
}
