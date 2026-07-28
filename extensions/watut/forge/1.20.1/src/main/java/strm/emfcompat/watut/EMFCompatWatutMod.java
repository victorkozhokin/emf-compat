package strm.emfcompat.watut;

import net.minecraftforge.fml.common.Mod;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

@Mod(EMFCompatWatutMod.MOD_ID)
public class EMFCompatWatutMod {
    public static final String MOD_ID = "emf_compat_watut";

    public static final String KEY_ENABLED = "watut.enabled";
    public static final String KEY_BODY_FOLLOW_ARMS = "watut.bodyFollowArms";

    public EMFCompatWatutMod() {
        ConfigRegistry.section(MOD_ID, "WATUT")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to WATUT (GUI / typing / idle poses).",
                        "Off", "Disable all EMF compatibility for WATUT.")
                .addBoolean(KEY_BODY_FOLLOW_ARMS, "Arm sync", true,
                        "Body-follow (new)",
                        "WATUT arm poses keep their reach shape and follow the moving torso.",
                        "Rotation-only (legacy)",
                        "WATUT arm poses keep only their rotation (arms keep EMF's walk position).");
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isBodyFollow() {
        return EMFCompatConfig.getBoolean(KEY_BODY_FOLLOW_ARMS, true);
    }
}
