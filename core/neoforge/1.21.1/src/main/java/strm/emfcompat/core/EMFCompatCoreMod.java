package strm.emfcompat.core;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import strm.emfcompat.core.client.EMFCompatCoreClient;

@Mod(EMFCompatCoreMod.MOD_ID)
public class EMFCompatCoreMod {

    public static final String MOD_ID = "emf_compat_core";

    public EMFCompatCoreMod(IEventBus modEventBus, ModContainer container) {
        EMFCompatConfig.init(FMLPaths.CONFIGDIR.get().resolve("emf_compat.json").toFile());
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
        if (net.neoforged.fml.ModList.get().isLoaded("sable")) {
            // Sable only: what its moving sub-levels do to the height a resource pack reads.
            ConfigRegistry.section(ConfigRegistry.CORE_ID, "Core")
                    .addBoolean(SubLevelHeight.KEY_ENABLED, "Sable: steady height on sub-levels", true,
                            "On", "On a moving Sable sub-level your resource pack sees only how you move on it, "
                                    + "not the sub-level rising and sinking: no landing animation each time it goes "
                                    + "down, no sway as it rises.",
                            "Off", "Your resource pack sees the height in the world, the sub-level's movement included.");
        }
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // EMF calls this back once per entity render, right after the pack animation.
            EMFCompatAnimationHook.register();
            BodyStates.registerVariables();
            EMFCompatCoreClient.registerConfigScreen(modEventBus, container);
        }
    }
}
