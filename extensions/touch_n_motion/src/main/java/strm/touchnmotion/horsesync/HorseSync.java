package strm.touchnmotion.horsesync;

import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;

/** Horse Sync: keeps the rider on a horse animated by EMF; the riding pose is {@link strm.touchnmotion.ride.HorseRide}'s. */
public final class HorseSync {

    public static final String KEY_ENABLED = "horsesync.enabled";
    public static final String KEY_RIDING_ANIMATION = "horsesync.ridingAnimation";
    public static final String KEY_JOCKEY = "horsesync.jockey";

    private HorseSync() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Horse sync", true,
                        "On", "Sync the ridden-horse animation onto the EMF player model.",
                        "Off", "Disable horse-sync EMF compatibility.")
                .addChild(KEY_ENABLED, KEY_RIDING_ANIMATION, "Riding animation", true,
                        "On", "On a horse the hands hold the reins over its withers and go with its head, the legs grip its sides; a hand you use lets go and the other keeps hold.",
                        "Off", "Leave the mounted pose to the vanilla / resource-pack animation.")
                .addChild(KEY_RIDING_ANIMATION, KEY_JOCKEY, "Jockey lean", true,
                        "On", "The rider leans over the horse's neck, sitting up while a hand is off the reins.",
                        "Off", "The rider sits upright.");
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    public static boolean isRidingAnimation() {
        return EMFCompatConfig.getBoolean(KEY_RIDING_ANIMATION, true);
    }

    /** Once the client is set up: EMF calls the hook back once per rendered entity, right after the pack animation. */
    public static void registerHook() {
        HorseSyncAnimationHook.register();
    }
}
