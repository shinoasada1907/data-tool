package com.universaldatatools.core.table;

import java.util.HashMap;
import java.util.Map;

/**
 * What a run has seen, by hashed key (core-01 TD11): about 50–100 bytes a key whatever the values. Callers bound it
 * with the dataset row limit. Not thread-safe.
 */
public final class KeyIndex<V> {

    private final Map<Hash128, V> entries = new HashMap<>();

    /** The value already held for {@code key}, or {@code null} after storing {@code value}. */
    public V putIfAbsent(Hash128 key, V value) {
        return entries.putIfAbsent(key, value);
    }

    public V get(Hash128 key) {
        return entries.get(key);
    }

    public int size() {
        return entries.size();
    }
}
