package strm.emfcompat.animationadditions.interaction;

import java.util.ArrayList;
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

    private static final List<EntityStates<?>> ALL = new ArrayList<>();

    public static final class Entry<T> {
        public final T value;
        long seenAt, solvedAt;

        Entry(T value) {
            this.value = value;
        }
    }

    private final Map<UUID, Entry<T>> entries = new HashMap<>();
    private final Supplier<T> make;

    public EntityStates(Supplier<T> make) {
        this.make = make;
        synchronized (ALL) {
            ALL.add(this);
        }
    }

    /** The entity is being drawn now: its entry, made on first sight. */
    public Entry<T> seen(UUID uuid, long now) {
        if (entries.size() > SWEEP_ABOVE) sweep(now);
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
        if (entries.size() > MAX_ENTITIES) entries.clear();
    }

    public int size() {
        return entries.size();
    }

    /** Leaving the world: nothing of it is kept. */
    public static void clearAll() {
        synchronized (ALL) {
            for (EntityStates<?> states : ALL) states.entries.clear();
        }
    }
}
