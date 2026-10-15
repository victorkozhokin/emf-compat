package strm.emfcompat.armorpacks;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

@Mod(ArmorPacksEMFCompat.MOD_ID)
public class ArmorPacksEMFCompat {

    public static final String MOD_ID = "emf_compat_armor_packs";
    public static final String KEY_FALLBACK = "armor_packs.vanilla_fallback";
    public static final String KEY_TRIMS = "armor_packs.unfitting_trims";

    public ArmorPacksEMFCompat(IEventBus modEventBus) {
        ConfigRegistry.section(MOD_ID, "Armor Packs")
                .addBoolean(KEY_FALLBACK, "Vanilla model for other armour", true,
                        "On", "Armour whose texture the pack did not draw for its model is worn with the vanilla model.",
                        "Off", "Every armour piece uses the pack's model, fitting texture or not.")
                .addBoolean(KEY_TRIMS, "Leave off unfitting trims", true,
                        "On", "A trim drawn for the other model is not drawn at all.",
                        "Off", "Trims are always drawn, even when they land in the wrong place.");
        modEventBus.addListener(this::onReloadListeners);
    }

    public static boolean fallbackEnabled() {
        return EMFCompatConfig.getBoolean(KEY_FALLBACK, true);
    }

    public static boolean trimCheckEnabled() {
        return EMFCompatConfig.getBoolean(KEY_TRIMS, true);
    }

    private void onReloadListeners(RegisterClientReloadListenersEvent event) {
        // Packs can change any texture's size; the fallback models are rebuilt with EMF's roots anyway.
        event.registerReloadListener((ResourceManagerReloadListener) manager -> TextureSizes.clear());
    }
}
