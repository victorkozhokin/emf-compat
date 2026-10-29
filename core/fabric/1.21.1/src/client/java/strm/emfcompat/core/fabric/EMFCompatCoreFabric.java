package strm.emfcompat.core.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import strm.emfcompat.core.BodyStates;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.CrouchNormalizer;
import strm.emfcompat.core.SubLevelHeight;
import strm.emfcompat.core.EMFCompatAnimationHook;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.EMFCompatCore;
import strm.emfcompat.core.PoseInterpolator;

/**
 * Fabric entry point for the core EMF compatibility framework.
 *
 * <p>The pose pipeline itself is static and mixin-driven; this initializer loads the shared
 * config and registers the Core tab. The settings screen is reached through Mod Menu (see
 * {@link ModMenuIntegration}), which is optional — without it the config file is still read
 * and applied, there is just no button to open the screen.</p>
 */
public class EMFCompatCoreFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // EMF calls this back once per entity render, right after the pack animation.
        EMFCompatAnimationHook.register();
        BodyStates.registerVariables();
        EMFCompatConfig.init(FabricLoader.getInstance().getConfigDir().resolve("emf_compat.json").toFile());
        // Core tab, shown first and selected by default. Addons register their own sections.
        ConfigRegistry.section(ConfigRegistry.CORE_ID, "Core")
                .addBoolean(EMFCompatCore.KEY_COMPAT_ENABLED, "EMF compatibility", true,
                        "On", "Every installed addon works as configured in its own tab.",
                        "Off", "Turn off every EMF compatibility addon at once — the game behaves "
                                + "as if only EMF and your resource pack were installed. "
                                + "Applies immediately, no restart needed.")
                .addBoolean(PoseInterpolator.KEY_ENABLED, "Smooth pose transitions", true,
                        "On", "Blend between a mod's pose and your resource pack's animation when "
                                + "one starts, ends or changes, instead of switching in one frame.",
                        "Off", "Switch straight to the pose, as before.")
                .addBoolean(CrouchNormalizer.KEY_ENABLED, "Crouch fix", true,
                        "On", "Keep a crouching player at the right height while a mod animates them. "
                                + "Without it the model can sink into the ground or drop lower than "
                                + "the crouch, and jump up and down with every attack.",
                        "Off", "Leave the crouch to the mods' animations and your resource pack.");
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sable")) {
            // Sable only: what its moving sub-levels do to the height a resource pack reads.
            ConfigRegistry.section(ConfigRegistry.CORE_ID, "Core")
                    .addBoolean(SubLevelHeight.KEY_ENABLED, "Sable: steady height on sub-levels", true,
                            "On", "On a moving Sable sub-level your resource pack sees only how you move on it, "
                                    + "not the sub-level rising and sinking: no landing animation each time it goes "
                                    + "down, no sway as it rises.",
                            "Off", "Your resource pack sees the height in the world, the sub-level's movement included.");
        }
    }
}
