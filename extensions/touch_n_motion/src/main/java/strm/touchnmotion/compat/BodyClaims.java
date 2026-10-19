package strm.touchnmotion.compat;

import strm.emfcompat.core.BodyStates;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.touchnmotion.platform.Platform;

import java.util.Set;
import java.util.UUID;

/**
 * The parts of a player other mods have taken, by the states their addons set
 * ({@link BodyStates}): carrying something with Carry On takes the arms, a TACZ gun the arms, aiming
 * it the torso and the head too. What this mod adds there is left out while they are taken - the
 * hands' reaches and gestures with the arms, the narrow-gap turn and the lean on a wall that drag
 * the held arms off; the lean with the motion is halved for the arms and dropped for the torso; the
 * look for the head. The feet and the rest stay.
 */
public final class BodyClaims {
    public static final String KEY_CARRYON = "compat.carryon";
    public static final String KEY_TACZ = "compat.tacz";

    /** What still runs with the arms taken: the look, and the rides, which carry the body as a seat. */
    private static final Set<String> ARMS_FREE = Set.of("LookAt", "BoatRide", "BoatPassenger", "MinecartRide", "HorseRide", "Furniture");

    private BodyClaims() {
    }

    public static void register(ConfigRegistry.Group config) {
        if (Platform.isModLoaded("carryon")) {
            config.addBoolean(KEY_CARRYON, "Carry On Compat", true,
                    "On", "While carrying, the arms are left to Carry On: no reaching, no gestures, no turning in narrow gaps or leaning on walls, and half the lean with the motion.",
                    "Off", "This mod's motions go on while carrying.");
        }
        if (Platform.isModLoaded("tacz")) {
            config.addBoolean(KEY_TACZ, "TACZ Compat", true,
                    "On", "With a gun in hand the arms are left to TACZ as when carrying; while aiming the torso and the head are too.",
                    "Off", "This mod's motions go on with a gun in hand.");
        }
    }

    /** Whether a state counts: the other mods' ones, each while its switch here is on; never this mod's own. */
    private static boolean counted(String name) {
        if (name.startsWith("tnm_")) return false;
        if (BodyStates.CARRYING.equals(name)) return EMFCompatConfig.getBoolean(KEY_CARRYON, true);
        if (BodyStates.HOLDING_GUN.equals(name) || BodyStates.AIMING.equals(name)) return EMFCompatConfig.getBoolean(KEY_TACZ, true);
        return true;
    }

    public static boolean arms(UUID uuid) {
        return BodyStates.holds(uuid, BodyStates.Part.ARMS, BodyClaims::counted);
    }

    public static boolean torso(UUID uuid) {
        return BodyStates.holds(uuid, BodyStates.Part.TORSO, BodyClaims::counted);
    }

    public static boolean head(UUID uuid) {
        return BodyStates.holds(uuid, BodyStates.Part.HEAD, BodyClaims::counted);
    }

    /** Why a feature sits out now, or {@code null}: the arms, or the head for the look, are taken. */
    public static String yields(UUID uuid, String feature) {
        if ("LookAt".equals(feature)) return head(uuid) ? "head-taken" : null;
        return !ARMS_FREE.contains(feature) && arms(uuid) ? "arms-taken" : null;
    }

    /** How much of the lean with the motion is kept: all, half with the arms taken, none with the torso. */
    public static float motionShare(UUID uuid) {
        if (torso(uuid)) return 0f;
        return arms(uuid) ? 0.5f : 1f;
    }
}
