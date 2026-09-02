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
                                + "Applies immediately, no restart needed.");
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // EMF calls this back once per entity render, right after the pack animation.
            EMFCompatAnimationHook.register();
            EMFCompatCoreClient.registerConfigScreen(container);
        }
    }
}
