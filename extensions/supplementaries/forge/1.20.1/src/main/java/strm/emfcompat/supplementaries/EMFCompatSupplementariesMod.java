package strm.emfcompat.supplementaries;

import net.minecraftforge.fml.common.Mod;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

@Mod(EMFCompatSupplementariesMod.MOD_ID)
public class EMFCompatSupplementariesMod {

    public static final String MOD_ID = "emf_compat_supplementaries";

    public static final String KEY_ENABLED = "supplementaries.enabled";
    public static final String KEY_BODY_FOLLOW_ARMS = "supplementaries.bodyFollowArms";

    public EMFCompatSupplementariesMod() {
        ConfigRegistry.section(MOD_ID, "Supplementaries")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to Supplementaries item poses (flute, slingshot, bubble blower).",
                        "Off", "Disable all EMF compatibility for Supplementaries.")
                .addBoolean(KEY_BODY_FOLLOW_ARMS, "Arm sync", true,
                        "Body-follow (new)", "Item-hold arm poses keep their shape and follow the moving torso.",
                        "Rotation-only (legacy)", "Item-hold arm poses keep only their rotation.");
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isBodyFollow() {
        return EMFCompatConfig.getBoolean(KEY_BODY_FOLLOW_ARMS, true);
    }
}
