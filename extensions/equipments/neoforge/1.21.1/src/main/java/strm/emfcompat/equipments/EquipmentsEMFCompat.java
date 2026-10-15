package strm.emfcompat.equipments;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

@Mod(EquipmentsEMFCompat.MOD_ID)
public class EquipmentsEMFCompat {

    public static final String MOD_ID = "emf_compat_equipments";
    public static final String KEY_FALLBACK = "equipments.armor.vanilla_fallback";
    public static final String KEY_TRIMS = "equipments.armor.unfitting_trims";
    public static final String KEY_NML_SHELL = "equipments.nomansland.tortoise_shell";

    public EquipmentsEMFCompat(IEventBus modEventBus) {
        ConfigRegistry.Section section = ConfigRegistry.section(MOD_ID, "Equipments");
        section.group("armor_packs", "Armour packs")
                .addBoolean(KEY_FALLBACK, "Vanilla model for other armour", true,
                        "On", "Armour whose texture the pack did not draw for its model is worn with the vanilla model.",
                        "Off", "Every armour piece uses the pack's model, fitting texture or not.")
                .addBoolean(KEY_TRIMS, "Leave off unfitting trims", true,
                        "On", "A trim drawn for the other model is not drawn at all.",
                        "Off", "Trims are always drawn, even when they land in the wrong place.");
        section.group("nomansland", "No Man's Land")
                .addBoolean(KEY_NML_SHELL, "Tortoise shell on the back", true,
                        "On", "The tortoise shell follows the torso of an animated player.",
                        "Off", "The shell keeps No Man's Land's own fixed place and leaves the torso when it moves.");
        modEventBus.addListener(this::onReloadListeners);
    }

    public static boolean nmlShellEnabled() {
        return EMFCompatConfig.getBoolean(KEY_NML_SHELL, true);
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
