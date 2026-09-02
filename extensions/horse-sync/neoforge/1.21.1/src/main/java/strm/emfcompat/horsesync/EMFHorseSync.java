package strm.emfcompat.horsesync;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.horsesync.compat.EMFCompat;

@Mod(EMFHorseSync.MOD_ID)
public class EMFHorseSync {

    public static final String MOD_ID = "emf_compat_horse_sync";
    public static final String KEY_ENABLED = "horsesync.enabled";
    public static final String KEY_RIDING_ANIMATION = "horsesync.ridingAnimation";

    /** Pose source name for the riding animation. */
    public static final String RIDING_SOURCE = "horse_riding";

    public EMFHorseSync(IEventBus modEventBus, ModContainer modContainer) {
        // The riding seat is a low-priority base: action poses (guns, attacks) take the arms while
        // the seat keeps the legs/body.
        PoseManager.setSourcePriority(RIDING_SOURCE, -10);

        ConfigRegistry.section(MOD_ID, "Horse Sync")
                .addBoolean(KEY_ENABLED, "EMF compatibility", true,
                        "On", "Sync the ridden-horse animation onto the EMF player model.",
                        "Off", "Disable horse-sync EMF compatibility.")
                .addBoolean(KEY_RIDING_ANIMATION, "Riding animation", true,
                        "On", "Play a proper riding pose (legs straddling, hands on the reins) while on a horse.",
                        "Off", "Leave the mounted pose to the vanilla / resource-pack animation.");
        modEventBus.addListener(this::onClientSetup);
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isRidingAnimation() {
        return EMFCompatConfig.getBoolean(KEY_RIDING_ANIMATION, true);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        EMFCompat.init();
        // EMF calls this back once per rendered entity, right after the pack animation.
        HorseSyncAnimationHook.register();
    }
}
