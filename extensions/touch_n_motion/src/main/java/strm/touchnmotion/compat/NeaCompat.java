package strm.touchnmotion.compat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.EMFCompatCore;
import strm.touchnmotion.TouchNMotionMod;
import strm.touchnmotion.horsesync.HorseSync;
import strm.touchnmotion.platform.Platform;
import strm.touchnmotion.ride.BoatRide;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Not Enough Animations animates some of what this mod does - rowing a boat, the seat on a horse -
 * and with both at work a player is posed by one or the other as it falls out. This mod's are the
 * ones meant to show: while the option is on, Not Enough Animations' own for the same thing is
 * switched off in its settings as they are in memory, for as long as this mod's is on, and put
 * back as it was when either is turned off. Its settings file is not written.
 */
public final class NeaCompat {
    public static final String KEY_ENABLED = "compat.nea";
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatNea");
    private static final int EVERY_TICKS = 20;

    /** Not Enough Animations' switches, each with whether this mod does the same now. */
    private static final Map<String, BooleanSupplier> OURS = new LinkedHashMap<>();

    static {
        OURS.put("enableRowBoatAnimation", () -> EMFCompatConfig.getBoolean(BoatRide.KEY_ENABLED, true));
        BooleanSupplier riding = () -> HorseSync.isEnabled() && HorseSync.isRidingAnimation();
        OURS.put("enableHorseAnimation", riding);
        OURS.put("enableHorseLegAnimation", riding);
    }

    /** What each switch was before this mod turned it off. */
    private static final Map<String, Boolean> WAS = new LinkedHashMap<>();
    private static Boolean present;
    private static boolean failed;
    private static int ticks;

    private NeaCompat() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "NEA Compat", true,
                "On", "With Not Enough Animations installed, its rowing and horse-riding animations are switched off while this mod's are on: this mod's are the ones shown.",
                "Off", "Not Enough Animations' rowing and horse riding are left as they are set; they and this mod's may show by turns.");
    }

    /** Once a second: each of its switches is as this mod's settings want it. */
    public static void tick() {
        if (failed || ticks++ % EVERY_TICKS != 0) return;
        if (present == null) present = Platform.isModLoaded("notenoughanimations");
        if (!present) return;
        try {
            Object config = Class.forName("dev.tr7zw.notenoughanimations.versionless.NEABaseMod").getField("config").get(null);
            if (config == null) return;
            boolean on = EMFCompatCore.isCompatEnabled() && EMFCompatConfig.getBoolean(TouchNMotionMod.KEY_ENABLED, true)
                    && EMFCompatConfig.getBoolean(KEY_ENABLED, true);
            boolean changed = false;
            for (Map.Entry<String, BooleanSupplier> each : OURS.entrySet()) {
                Field field = config.getClass().getField(each.getKey());
                boolean off = on && each.getValue().getAsBoolean();
                if (off && field.getBoolean(config)) {
                    WAS.putIfAbsent(each.getKey(), true);
                    field.setBoolean(config, false);
                    changed = true;
                } else if (!off && WAS.remove(each.getKey()) != null && !field.getBoolean(config)) {
                    field.setBoolean(config, true);
                    changed = true;
                }
            }
            if (changed) refresh();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed = true;
            LOGGER.warn("[NeaCompat] cannot reach Not Enough Animations' settings; its animations are left as they are", e);
        }
    }

    /** It keeps a list of the animations that are on, made when its settings change. */
    private static void refresh() throws ReflectiveOperationException {
        Class<?> loader = Class.forName("dev.tr7zw.notenoughanimations.NEAnimationsLoader");
        Object instance = loader.getField("INSTANCE").get(null);
        if (instance == null) return;
        Object provider = loader.getField("animationProvider").get(instance);
        if (provider != null) provider.getClass().getMethod("refreshEnabledAnimations").invoke(provider);
        if (strm.touchnmotion.DebugLog.decisions()) LOGGER.info("[NeaCompat] kept off now: {}", WAS.keySet());
    }
}
