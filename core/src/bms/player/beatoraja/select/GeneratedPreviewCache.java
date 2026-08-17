package bms.player.beatoraja.select;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small synchronized LRU used by the background preview pipeline. */
final class GeneratedPreviewCache<K, V> {

    private final Map<K, V> entries;

    GeneratedPreviewCache(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("cache limit must be positive");
        }
        entries = new LinkedHashMap<>(limit, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > limit;
            }
        };
    }

    synchronized V get(K key) {
        return entries.get(key);
    }

    synchronized void put(K key, V value) {
        entries.put(key, value);
    }

    synchronized int size() {
        return entries.size();
    }

    synchronized boolean contains(K key) {
        return entries.containsKey(key);
    }
}
