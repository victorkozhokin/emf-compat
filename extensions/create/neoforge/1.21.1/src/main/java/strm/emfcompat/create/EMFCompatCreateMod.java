package strm.emfcompat.create;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

@Mod(EMFCompatCreateMod.MOD_ID)
public class EMFCompatCreateMod {

    public static final String MOD_ID = "emf_compat_create";

    public static final String KEY_ENABLED = "create.enabled";
    public static final String KEY_BODY_FOLLOW_ARMS = "create.bodyFollowArms";
    public static final String KEY_SKYHOOK = "create.skyhook";
    public static final String KEY_AERONAUTICS = "create.aeronautics";
    public static final String KEY_GRAPPLING = "create.grapplingHooks";
    public static final String KEY_RAGDOLL = "create.ragdoll";
    public static final String KEY_COSMONAUTICS = "create.cosmonautics";
    public static final String KEY_CREATE_SA = "create.createSa";
    public static final String KEY_NEA_ITEMSWAP = "create.neaItemSwap";

    public EMFCompatCreateMod(IEventBus modEventBus) {
        registerConfig();
    }

    private void registerConfig() {
        ConfigRegistry.Section section = ConfigRegistry.section(MOD_ID, "Create")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Apply EMF compatibility to Create and its add-ons.",
                        "Off", "Disable all EMF compatibility for Create.")
                .addBoolean(KEY_BODY_FOLLOW_ARMS, "Arm sync", true,
                        "Body-follow (new)",
                        "Captured arm poses (handle, ragdoll, Stuff 'N Additions items) keep their shape and follow the torso.",
                        "Rotation-only (legacy)",
                        "Captured arm poses keep only their rotation.")
                .addBoolean(KEY_SKYHOOK, "Skyhook", true,
                        "On", "Keep Create's skyhook hang pose while EMF is active (capture & restore).",
                        "Off", "Leave skyhook to EMF (resource-pack animations keep playing).");
        // Per-add-on toggles, shown only when the add-on is present.
        if (CreateMods.AERONAUTICS) {
            section.addBoolean(KEY_AERONAUTICS, "Aeronautics handle", true,
                    "On", "Keep the arms on the Aeronautics handle grip pose.",
                    "Off", "Leave the handle arms to EMF.");
        }
        if (CreateMods.GRAPPLING_HOOKS) {
            section.addBoolean(KEY_GRAPPLING, "Grappling Hooks", true,
                    "On", "Force the vanilla model / pause EMF while grappling.",
                    "Off", "Leave grappling to EMF.");
        }
        if (CreateMods.RAGDOLL) {
            section.addBoolean(KEY_RAGDOLL, "Sable Ragdolls", true,
                    "On", "Keep the arms on the ragdoll grab pose.",
                    "Off", "Leave the grab arms to EMF.");
        }
        if (CreateMods.COSMONAUTICS) {
            section.addBoolean(KEY_COSMONAUTICS, "Cosmonautics flight", true,
                    "On", "Play the pack's flight animation while flying a Cosmonautics jetpack.",
                    "Off", "Leave Cosmonautics flight to EMF.");
        }
        if (CreateMods.CREATE_SA) {
            section.addBoolean(KEY_CREATE_SA, "Stuff 'N Additions", true,
                    "On", "Jetpack flight animation + arm poses for the whisk and block picker.",
                    "Off", "Leave Stuff 'N Additions to EMF.");
        }
        if (CreateMods.NEA) {
            section.addBoolean(KEY_NEA_ITEMSWAP, "NEA item-swap fix", true,
                    "On", "Suppress NotEnoughAnimations' item-swap animation during Create activities.",
                    "Off", "Let NEA play its item-swap animation.");
        }
    }

    // ---- Runtime config helpers ----------------------------------------------------------

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isBodyFollow() {
        return EMFCompatConfig.getBoolean(KEY_BODY_FOLLOW_ARMS, true);
    }

    public static boolean isSkyhook() {
        return EMFCompatConfig.getBoolean(KEY_SKYHOOK, true);
    }

    public static boolean isAeronautics() {
        return EMFCompatConfig.getBoolean(KEY_AERONAUTICS, true);
    }

    public static boolean isGrappling() {
        return EMFCompatConfig.getBoolean(KEY_GRAPPLING, true);
    }

    public static boolean isRagdoll() {
        return EMFCompatConfig.getBoolean(KEY_RAGDOLL, true);
    }

    public static boolean isCosmonautics() {
        return EMFCompatConfig.getBoolean(KEY_COSMONAUTICS, true);
    }

    public static boolean isCreateSa() {
        return EMFCompatConfig.getBoolean(KEY_CREATE_SA, true);
    }

    public static boolean isNeaItemSwap() {
        return EMFCompatConfig.getBoolean(KEY_NEA_ITEMSWAP, true);
    }
}
