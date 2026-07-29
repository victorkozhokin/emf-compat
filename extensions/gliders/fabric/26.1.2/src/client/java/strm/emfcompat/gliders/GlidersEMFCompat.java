package strm.emfcompat.gliders;

import net.fabricmc.api.ClientModInitializer;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.gliders.compat.GlidingState;

/**
 * Fabric client entry point for the Gliders EMF compatibility addon.
 *
 * <p>Only <b>Reliable Gliders</b> is covered on this version: Paragliders and vc_gliders have no
 * Fabric build for it, so their toggles from the NeoForge/Forge builds would have nothing to
 * switch. The deploy-phase handling is likewise absent — it reads a Player Animator layer on
 * those loaders, and Reliable Gliders uses no animation library at all here.</p>
 */
public class GlidersEMFCompat implements ClientModInitializer {

    public static final String MOD_ID = "emf_compat_gliders";

    public static final String KEY_ENABLED = "gliders.enabled";
    public static final String KEY_BODY_FOLLOW_ARMS = "gliders.bodyFollowArms";
    public static final String KEY_RELIABLE_GLIDERS = "gliders.reliableGliders";

    @Override
    public void onInitializeClient() {
        ConfigRegistry.Section section = ConfigRegistry.section(MOD_ID, "Gliders")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to gliders (hold the gliding arm pose over EMF).",
                        "Off", "Disable all EMF compatibility for gliders.")
                .addBoolean(KEY_BODY_FOLLOW_ARMS, "Arm sync", true,
                        "Body-follow (new)",
                        "Gliding arm poses keep their shape and follow the moving torso.",
                        "Rotation-only (legacy)",
                        "Gliding arm poses keep only their rotation.");
        if (GlidingState.isReliableGlidersLoaded()) {
            section.addBoolean(KEY_RELIABLE_GLIDERS, "Reliable Gliders", true,
                    "On", "Hold the Reliable Gliders pose while gliding.",
                    "Off", "Leave Reliable Gliders to EMF.");
        }
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isBodyFollow() {
        return EMFCompatConfig.getBoolean(KEY_BODY_FOLLOW_ARMS, true);
    }

    public static boolean isReliableGlidersEnabled() {
        return EMFCompatConfig.getBoolean(KEY_RELIABLE_GLIDERS, true);
    }
}
