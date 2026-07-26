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
        ConfigRegistry.section(ConfigRegistry.CORE_ID, "Core");
        if (FMLEnvironment.dist == Dist.CLIENT) {
            EMFCompatCoreClient.registerConfigScreen(container);
        }
    }
}
