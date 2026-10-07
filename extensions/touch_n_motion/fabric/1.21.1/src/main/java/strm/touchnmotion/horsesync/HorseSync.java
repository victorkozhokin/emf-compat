package strm.touchnmotion.horsesync;

import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.PoseManager;

/** Horse Sync: keeps the rider on a horse animated by EMF and gives them a riding pose. */
public final class HorseSync {

    public static final String KEY_ENABLED = "horsesync.enabled";
    public static final String KEY_RIDING_ANIMATION = "horsesync.ridingAnimation";

    /** Pose source name for the riding animation. */
    public static final String RIDING_SOURCE = "horse_riding";

    private HorseSync() {
    }

    public static void register(ConfigRegistry.Group config) {
        // The riding seat is a low-priority base: action poses (guns, attacks) take the arms while
        // the seat keeps the legs/body.
        PoseManager.setSourcePriority(RIDING_SOURCE, -10);

        config.addBoolean(KEY_ENABLED, "Horse sync", true,
                        "On", "Sync the ridden-horse animation onto the EMF player model.",
                        "Off", "Disable horse-sync EMF compatibility.")
                .addChild(KEY_ENABLED, KEY_RIDING_ANIMATION, "Riding animation", true,
                        "On", "Play a proper riding pose (legs straddling, hands on the reins) while on a horse.",
                        "Off", "Leave the mounted pose to the vanilla / resource-pack animation.");
        // EMF calls this back once per rendered entity, right after the pack animation.
        HorseSyncAnimationHook.register();
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isRidingAnimation() {
        return EMFCompatConfig.getBoolean(KEY_RIDING_ANIMATION, true);
    }

}
