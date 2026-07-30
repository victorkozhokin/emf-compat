package strm.emfcompat.takeaseat;

import net.minecraftforge.fml.common.Mod;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

/**
 * Take a Seat extension entry point. On 1.20.1 the target is <b>Sitting+</b>: Take a Seat is a
 * fork of it and has no 1.20.1 build, so the same module covers the original mod here.
 */
@Mod(TakeASeatEMFCompat.MOD_ID)
public class TakeASeatEMFCompat {

    public static final String MOD_ID = "emf_compat_takeaseat";

    public static final String KEY_ENABLED = "takeaseat.enabled";
    public static final String KEY_BODY_FOLLOW_ARMS = "takeaseat.bodyFollowArms";

    public TakeASeatEMFCompat() {
        ConfigRegistry.section(MOD_ID, "Sitting+")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to Sitting+ sitting poses.",
                        "Off", "Disable all EMF compatibility for Sitting+.")
                .addBoolean(KEY_BODY_FOLLOW_ARMS, "Arm sync", true,
                        "Body-follow (new)",
                        "Sitting arm poses keep their exact shape and follow the moving torso.",
                        "Rotation-only (legacy)",
                        "Sitting arm poses keep only their rotation.");
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isBodyFollow() {
        return EMFCompatConfig.getBoolean(KEY_BODY_FOLLOW_ARMS, true);
    }
}
