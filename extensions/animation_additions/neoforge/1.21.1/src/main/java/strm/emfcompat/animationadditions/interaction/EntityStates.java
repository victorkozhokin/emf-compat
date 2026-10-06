package strm.emfcompat.animationadditions.interaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Per-entity state of one feature, with the lifecycle every feature shares: solve at most every
 * {@link #SOLVE_EVERY_NANOS}, stop showing a result the entity has not been drawn with for
 * {@link #STALE_NANOS}, forget an entity not drawn for {@link #FORGET_NANOS}, and drop everything
 * when the player leaves the world ({@link #clearAll}).
 */
public final class EntityStates<T> {

    public static final long SOLVE_EVERY_NANOS = 2_000_000L;
    public static final long STALE_NANOS = 200_000_000L;
    public static final long FORGET_NANOS = 5_000_000_000L;
    private static final int SWEEP_ABOVE = 32;
    private static final int MAX_ENTITIES = 256;
    private static final long SWEEP_EVERY_NANOS = 1_000_000_000L;

    private static final List<EntityStates<?>> ALL = new ArrayList<>();
    private static final List<Map<UUID, ?>> OTHERS = new ArrayList<>();

    public static final class Entry<T> {
        public final T value;
        long seenAt, solvedAt;

        Entry(T value) {
            this.value = value;
        }
    }

    private final Map<UUID, Entry<T>> entries = new HashMap<>();
    private final Supplier<T> make;
    private long sweptAt;

    public EntityStates(Supplier<T> make) {
        this.make = make;
        synchronized (ALL) {
            ALL.add(this);
        }
    }

    /** The entity is being drawn now: its entry, made on first sight. */
    public Entry<T> seen(UUID uuid, long now) {
        // With many players in view every one of them, in every feature, would walk the whole map
        // on every frame: once a second is enough, and at once only when it has outgrown its bound.
        if (entries.size() > MAX_ENTITIES || entries.size() > SWEEP_ABOVE && now - sweptAt > SWEEP_EVERY_NANOS) {
            sweptAt = now;
            sweep(now);
        }
        Entry<T> entry = entries.computeIfAbsent(uuid, k -> new Entry<>(make.get()));
        entry.seenAt = now;
        return entry;
    }

    /**
     * Whether to solve this frame: seconds since the last solve (0 on the first), or -1 when the
     * last one was less than {@link #SOLVE_EVERY_NANOS} ago. Marks the entry solved now.
     */
    public static double due(Entry<?> entry, long now) {
        if (entry.solvedAt != 0 && now - entry.solvedAt < SOLVE_EVERY_NANOS) return -1;
        double dt = entry.solvedAt == 0 ? 0 : Math.min(0.1, (now - entry.solvedAt) / 1e9);
        entry.solvedAt = now;
        return dt;
    }

    /** The state of an entity drawn in the last {@link #STALE_NANOS}, else {@code null}. */
    public T fresh(UUID uuid) {
        Entry<T> entry = entries.get(uuid);
        if (entry == null || System.nanoTime() - entry.seenAt > STALE_NANOS) return null;
        return entry.value;
    }

    private void sweep(long now) {
        Iterator<Entry<T>> it = entries.values().iterator();
        while (it.hasNext()) if (now - it.next().seenAt > FORGET_NANOS) it.remove();
        // Still too many, all of them drawn lately: the ones seen longest ago go, never the lot -
        // the one being drawn now would lose its state with them every frame.
        int over = entries.size() - MAX_ENTITIES;
        if (over <= 0) return;
        long[] seen = new long[entries.size()];
        int i = 0;
        for (Entry<T> entry : entries.values()) seen[i++] = entry.seenAt;
        Arrays.sort(seen);
        long cutoff = seen[over - 1];
        it = entries.values().iterator();
        while (it.hasNext() && entries.size() > MAX_ENTITIES) if (it.next().seenAt <= cutoff) it.remove();
    }

    /** Drop a feature's old pose when another system takes exclusive ownership. */
    public void forget(UUID uuid) {entries.remove(uuid);}

    public int size() {
        return entries.size();
    }

    /** A map by player or entity kept outside of here: emptied with everything else on leaving the world. */
    public static <M extends Map<UUID, ?>> M alsoClear(M map) {
        synchronized (ALL) {
            OTHERS.add(map);
        }
        return map;
    }

    /** Leaving the world: nothing of it is kept. */
    public static void clearAll() {
        synchronized (ALL) {
            for (EntityStates<?> states : ALL) states.entries.clear();
            for (Map<UUID, ?> map : OTHERS) map.clear();
        }
    }
}
