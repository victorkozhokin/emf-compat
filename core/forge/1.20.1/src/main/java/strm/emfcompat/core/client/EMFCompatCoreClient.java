package strm.emfcompat.core.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import strm.emfcompat.core.ConfigRegistry;

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
        // Mod construction is parallel. Wait until all addons have registered their tabs,
        // then update their containers on the client thread after the setup event finishes.
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (FMLClientSetupEvent event) -> event.enqueueWork(EMFCompatCoreClient::registerAddonConfigScreens));
    }

    private static void registerAddonConfigScreens() {
        for (ConfigRegistry.Section section : ConfigRegistry.orderedSections()) {
            if (section.id.equals(ConfigRegistry.CORE_ID)) {
                continue;
            }
            ModList.get().getModContainerById(section.id).ifPresent(container -> {
                if (container.getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class).isEmpty()) {
                    container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                            () -> new ConfigScreenHandler.ConfigScreenFactory(
                                    (minecraft, parent) -> new ConfigScreen(parent, section.id)));
                }
            });
        }
    }
}
