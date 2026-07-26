package strm.emfcompat.core.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only setup for the core module. Kept out of the common {@code @Mod} class so its
 * client-only references (screen, config-screen factory) never load on a dedicated server.
 */
public final class EMFCompatCoreClient {

    private EMFCompatCoreClient() {
    }

    /** Registers the config screen so a "Config" button appears in the NeoForge mod list. */
    public static void registerConfigScreen(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> new ConfigScreen(parent));
    }
}
