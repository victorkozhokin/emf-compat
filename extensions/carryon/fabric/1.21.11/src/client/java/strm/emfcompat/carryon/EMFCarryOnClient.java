package strm.emfcompat.carryon;

import net.fabricmc.api.ClientModInitializer;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

/**
 * Fabric client entry point for the Carry On EMF compatibility addon.
 * Registers the addon's tab; all rendering work is done by the client-side mixins.
 */
public class EMFCarryOnClient implements ClientModInitializer {

    public static final String MOD_ID = "emf_compat_carry_on";

    /** Master switch: apply EMF compatibility to Carry On at all. */
    public static final String KEY_ENABLED = "carryon.enabled";
    /** Arm-sync mode: body-follow (new) vs the legacy rotation-only system. */
    public static final String KEY_BODY_FOLLOW_ARMS = "carryon.bodyFollowArms";
    /** Force a carried mob to render with its vanilla model (no EMF animation). */
    public static final String KEY_FORCE_VANILLA_CARRIED = "carryon.forceVanillaCarriedModel";

    @Override
    public void onInitializeClient() {
        // EMF calls this back once per entity render, right after the pack animation.
        CarryOnAnimationHook.register();
        ConfigRegistry.section(MOD_ID, "Carry On")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to Carry On (arm pose and carried-object sync).",
                        "Off", "Disable all EMF compatibility for Carry On (plain Carry On behaviour).")
                .addBoolean(KEY_BODY_FOLLOW_ARMS, "Arm sync", true,
                        "Body-follow (new)",
                        "Arms keep their exact raised pose and follow the moving torso; the carried object follows with them.",
                        "Rotation-only (legacy)",
                        "Arms keep only their rotation; the carried object is synced to the torso the old way (with rotation).")
                .addBoolean(KEY_FORCE_VANILLA_CARRIED, "Carried mob model", true,
                        "Vanilla",
                        "Force carried mobs to render with their vanilla model (no EMF/resource-pack animation).",
                        "EMF",
                        "Let carried mobs keep their EMF-animated model while being carried.");
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isBodyFollow() {
        return EMFCompatConfig.getBoolean(KEY_BODY_FOLLOW_ARMS, true);
    }

    public static boolean forceVanillaCarried() {
        return EMFCompatConfig.getBoolean(KEY_FORCE_VANILLA_CARRIED, true);
    }
}
