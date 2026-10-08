package strm.emfcompat.core.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import strm.emfcompat.core.ConfigRegistry;

/**
 * Client-only setup for the core module. Kept out of the common {@code @Mod} class so its
 * client-only references (screen, config-screen factory) never load on a dedicated server.
 */
public final class EMFCompatCoreClient {

    private EMFCompatCoreClient() {
    }

    /** Registers the config screen so a "Config" button appears in the NeoForge mod list. */
    public static void registerConfigScreen(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> new ConfigScreen(parent));
        // Mod construction is parallel. Wait until all addons have registered their tabs,
        // then update their containers on the client thread after the setup event finishes.
        modEventBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(EMFCompatCoreClient::registerAddonConfigScreens));
    }

    private static void registerAddonConfigScreens() {
        for (ConfigRegistry.Section section : ConfigRegistry.orderedSections()) {
            if (section.id.equals(ConfigRegistry.CORE_ID)) {
                continue;
            }
            ModList.get().getModContainerById(section.id).ifPresent(container -> {
                if (container.getCustomExtension(IConfigScreenFactory.class).isEmpty()) {
                    container.registerExtensionPoint(IConfigScreenFactory.class,
                            (mod, parent) -> new ConfigScreen(parent, section.id));
                }
            });
        }
    }
}
