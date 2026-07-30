package strm.emfcompat.tacz;

import net.minecraftforge.fml.common.Mod;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

/**
 * TACZ extension entry point. All rendering work is done by the mixins
 * ({@code PlayerModelMixin} captures gun arm/head poses into the core
 * {@code PoseManager}; core restores them onto EMF models). This class also
 * registers the TACZ config tab.
 */
@Mod(TACZEMFCompat.MOD_ID)
public class TACZEMFCompat {

    public static final String MOD_ID = "emf_compat_tacz";

    /** Master switch: apply EMF compatibility to TACZ at all. */
    public static final String KEY_ENABLED = "tacz.enabled";
    /** Arm-sync mode: body-follow (new) vs the legacy rotation-only system. */
    public static final String KEY_BODY_FOLLOW_ARMS = "tacz.bodyFollowArms";

    public TACZEMFCompat() {
        ConfigRegistry.section(MOD_ID, "TACZ")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to TACZ (gun arm poses and head aim).",
                        "Off", "Disable all EMF compatibility for TACZ (plain TACZ behaviour).")
                .addBoolean(KEY_BODY_FOLLOW_ARMS, "Arm sync", true,
                        "Body-follow (new)",
                        "Gun arm poses keep their exact shape and follow the moving torso.",
                        "Rotation-only (legacy)",
                        "Arms keep only their rotation — playerAnimator reload/aim arm movement is lost.");
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isBodyFollow() {
        return EMFCompatConfig.getBoolean(KEY_BODY_FOLLOW_ARMS, true);
    }
}
