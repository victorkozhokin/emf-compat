package strm.emfcompat.quark;

import net.minecraftforge.fml.common.Mod;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

@Mod(EMFCompatQuarkMod.MOD_ID)
public class EMFCompatQuarkMod {

    public static final String MOD_ID = "emf_compat_quark";

    public static final String KEY_ENABLED = "quark.enabled";
    public static final String KEY_BODY_FOLLOW_ARMS = "quark.bodyFollowArms";

    public EMFCompatQuarkMod() {
        ConfigRegistry.section(MOD_ID, "Quark")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to Quark emote poses.",
                        "Off", "Disable all EMF compatibility for Quark.")
                .addBoolean(KEY_BODY_FOLLOW_ARMS, "Arm sync", true,
                        "Body-follow (new)", "Emote arm poses keep their shape and follow the moving torso.",
                        "Rotation-only (legacy)", "Emote arm poses keep only their rotation.");
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isBodyFollow() {
        return EMFCompatConfig.getBoolean(KEY_BODY_FOLLOW_ARMS, true);
    }
}
