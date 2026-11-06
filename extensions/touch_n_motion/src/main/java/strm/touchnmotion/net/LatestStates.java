package strm.touchnmotion.net;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** Continuous input keeps its newest value; one-shot events flush it before they are sent. */
public final class LatestStates<K, V> {
    private final Map<K, V> pending = new LinkedHashMap<>();

    public void put(K key, V value) { pending.put(key, value); }

    public void flush(K key, BiConsumer<K, V> send) {
        V value = pending.remove(key);
        if (value != null) send.accept(key, value);
    }

    public void flush(BiConsumer<K, V> send) {
        var batch = new LinkedHashMap<>(pending);
        pending.clear();
        batch.forEach(send);
    }
}
