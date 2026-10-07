package strm.touchnmotion;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import strm.emfcompat.core.client.ConfigScreen;

/**
 * Mod Menu entry point: the mod's own entry in the mod list opens the settings - the shared
 * screen, on this mod's tab. Only ever loaded by Mod Menu itself, so nothing here runs, or
 * fails, when Mod Menu is absent.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ConfigScreen(parent, TouchNMotionMod.MOD_ID);
    }
}
