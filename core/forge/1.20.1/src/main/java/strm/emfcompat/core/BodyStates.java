package strm.emfcompat.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.utils.EMFEntity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What each player is doing that other mods may want to know: carrying something, holding a gun,
 * aiming, reaching for a block... A state is a name and a value - 0 to 1, or an angle in radians
 * for a lean - and the parts of the body
 * it has taken: an addon sets it while it lasts, and whoever adds motion of its own leaves those
 * parts alone ({@link #holds}). A state not set again goes stale on its own.
 *
 * <p>Resource packs read the same states as EMF variables, {@code emfc_<name>} - each name in
 * {@link #VARIABLES} is known to EMF whenever this mod is installed, so a pack that reads one does
 * not break where the mod setting it is missing; it reads 0 there.</p>
 */
public final class BodyStates {
    private static final Logger LOGGER = LoggerFactory.getLogger("emf_compat");

    public enum Part {ARMS, TORSO, HEAD, LEGS}

    /** Carry On: something is carried. */
    public static final String CARRYING = "carrying";
    /** TACZ: a gun is held. */
    public static final String HOLDING_GUN = "holding_gun";
    /** TACZ: a gun is aimed. */
    public static final String AIMING = "aiming";

    /** Every state packs can read, as {@code emfc_<name>}: the addons' and Touch'n Motion's. */
    public static final String[] VARIABLES = {
            CARRYING, HOLDING_GUN, AIMING,
            "tnm_reach", "tnm_hold", "tnm_lean", "tnm_gesture", "tnm_work", "tnm_ride",
            "tnm_right_arm", "tnm_left_arm", "tnm_head", "tnm_body",
            "tnm_lean_x", "tnm_lean_z"};

    /** A state not set again for this long is over. */
    private static final long FRESH_NANOS = 500_000_000L;

    private record Entry(float value, EnumSet<Part> parts, long at) {
    }

    private static final Map<UUID, Map<String, Entry>> STATES = new HashMap<>();
    private static long purgedAt;

    private BodyStates() {
    }

    /** Sets a state of a player, taking these parts while its value is above 0. */
    public static void set(UUID uuid, String name, float value, Part... parts) {
        if (uuid == null) return;
        long now = System.nanoTime();
        EnumSet<Part> taken = parts.length == 0 ? EnumSet.noneOf(Part.class) : EnumSet.of(parts[0], parts);
        STATES.computeIfAbsent(uuid, k -> new HashMap<>()).put(name, new Entry(value, taken, now));
        if (now - purgedAt > 5_000_000_000L) purge(now);
    }

    public static void clear(UUID uuid, String name) {
        Map<String, Entry> states = uuid == null ? null : STATES.get(uuid);
        if (states != null) states.remove(name);
    }

    /** The state's value, 0 when it is not set or stale. */
    public static float value(UUID uuid, String name) {
        Map<String, Entry> states = uuid == null ? null : STATES.get(uuid);
        Entry entry = states == null ? null : states.get(name);
        return entry == null || System.nanoTime() - entry.at > FRESH_NANOS ? 0f : entry.value;
    }

    /** Whether a state that {@code counted} accepts has this part of the player now. */
    public static boolean holds(UUID uuid, Part part, Predicate<String> counted) {
        Map<String, Entry> states = uuid == null ? null : STATES.get(uuid);
        if (states == null) return false;
        long now = System.nanoTime();
        for (Map.Entry<String, Entry> e : states.entrySet()) {
            Entry entry = e.getValue();
            if (entry.value > 0 && entry.parts.contains(part) && now - entry.at <= FRESH_NANOS && counted.test(e.getKey())) return true;
        }
        return false;
    }

    public static boolean holds(UUID uuid, Part part) {
        return holds(uuid, part, name -> true);
    }

    private static void purge(long now) {
        purgedAt = now;
        for (Iterator<Map<String, Entry>> players = STATES.values().iterator(); players.hasNext(); ) {
            Map<String, Entry> states = players.next();
            states.values().removeIf(entry -> now - entry.at > FRESH_NANOS);
            if (states.isEmpty()) players.remove();
        }
    }

    /** Called once from each loader's client init, before resources load: EMF knows the names from then on. */
    public static void registerVariables() {
        for (String name : VARIABLES) {
            try {
                EMFAnimationApi.registerSingletonAnimationVariable("emf_compat_core", "emfc_" + name,
                        "EMF Compat state '" + name + "' of the entity being animated (0 when unset)",
                        (Supplier<Float>) () -> current(name));
            } catch (Throwable t) {
                LOGGER.warn("[EMF Compat] could not register the EMF variable emfc_{}", name, t);
                return;
            }
        }
    }

    private static float current(String name) {
        try {
            EMFEntity entity = EMFAnimationApi.getCurrentEntity();
            return entity == null ? 0f : value(entity.etf$getUuid(), name);
        } catch (Throwable t) {
            return 0f;
        }
    }
}
