package strm.emfcompat.create;

import net.neoforged.fml.ModList;

/**
 * Presence flags for the optional Create-ecosystem mods this addon layers onto. Detected once
 * via class presence (matching the mixin plugin) or {@link ModList}, and used to register only
 * the relevant config toggles and gate the runtime layers.
 */
public final class CreateMods {

    public static final boolean AERONAUTICS =
            has("dev/simulated_team/simulated/content/blocks/handle/PlayerHoldingHandleRenderer.class");
    public static final boolean GRAPPLING_HOOKS =
            has("com/addon/gancho/AddonGanchoNet.class");
    public static final boolean RAGDOLL =
            has("dev/leo/sableplayerragdoll/neoforge/client/RagdollGrabState.class");
    public static final boolean NEA =
            has("dev/tr7zw/notenoughanimations/animations/hands/ItemSwapAnimation.class");
    public static final boolean COSMONAUTICS = ModList.get().isLoaded("rocketnautics");
    public static final boolean CREATE_SA = ModList.get().isLoaded("create_sa");

    private CreateMods() {
    }

    private static boolean has(String resourcePath) {
        return CreateMods.class.getClassLoader().getResource(resourcePath) != null;
    }
}
