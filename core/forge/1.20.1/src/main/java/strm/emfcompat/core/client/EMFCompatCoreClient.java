package strm.emfcompat.core.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Client-only setup for the core module. Kept out of the common {@code @Mod} class so its
 * client-only references (screen, config-screen factory) never load on a dedicated server.
 */
public final class EMFCompatCoreClient {

    private EMFCompatCoreClient() {
    }

    /** Registers the config screen so a "Config" button appears in the Forge mod list. */
    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ConfigScreen(parent)));
    }
}
