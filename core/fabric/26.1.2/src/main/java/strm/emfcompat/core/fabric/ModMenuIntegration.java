package strm.emfcompat.core.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.client.ConfigScreen;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mod Menu entry point: puts the settings button on the core's entry in the mod list.
 *
 * <p>Fabric has no built-in config screen hook, so Mod Menu is the way in. It is an optional
 * dependency: this class is only ever loaded by Mod Menu itself through the {@code modmenu}
 * entrypoint, so nothing here runs — or fails — when Mod Menu is absent.</p>
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }

    /** Each installed addon opens the shared screen on its own registered tab. */
    @Override
    public Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        Map<String, ConfigScreenFactory<?>> factories = new LinkedHashMap<>();
        for (ConfigRegistry.Section section : ConfigRegistry.orderedSections()) {
            if (!section.id.equals(ConfigRegistry.CORE_ID)) {
                factories.put(section.id, parent -> new ConfigScreen(parent, section.id));
            }
        }
        return factories;
    }
}
