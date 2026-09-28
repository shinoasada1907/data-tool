package com.universaldatatools.platform.dataset;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.table.Delimiter;
import com.universaldatatools.core.table.ReadOptions;
import com.universaldatatools.core.table.TableInfo;
import com.universaldatatools.core.table.TextEncoding;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Remembers what inspecting a dataset with some options found (core-04 PL5), so a tool run right after a preview
 * does not read the whole file again. Parse failures are remembered too; I/O failures are not, since they may pass.
 * Least recently used entries go first. Lost on restart, which only costs a new inspection.
 */
public final class DatasetInspections {

    private record Key(UUID id, String sheet, Delimiter delimiter, TextEncoding encoding, Boolean hasHeader) {

        static Key of(UUID id, ReadOptions options) {
            return new Key(id, options.sheet(), options.delimiter(), options.encoding(), options.hasHeader());
        }
    }

    /** The outcome of one inspection: its result or the parse error it ended with. */
    private record Outcome(TableInfo info, DomainException failure) {
    }

    private final Map<Key, Outcome> entries;

    public DatasetInspections(int capacity) {
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, Outcome> eldest) {
                return size() > capacity;
            }
        };
    }

    /** The cached outcome, or {@code inspect}'s, remembered; a cached parse error is thrown again. */
    public TableInfo get(UUID id, ReadOptions options, Supplier<TableInfo> inspect) {
        Key key = Key.of(id, options);
        Outcome cached;
        synchronized (entries) {
            cached = entries.get(key);
        }
        if (cached == null) {
            try {
                cached = new Outcome(inspect.get(), null);
            } catch (DomainException e) {
                cached = new Outcome(null, e);
            }
            synchronized (entries) {
                entries.put(key, cached);
            }
        }
        if (cached.failure() != null) {
            throw cached.failure();
        }
        return cached.info();
    }

    public void evict(UUID id) {
        synchronized (entries) {
            entries.keySet().removeIf(key -> key.id().equals(id));
        }
    }

    int size() {
        synchronized (entries) {
            return entries.size();
        }
    }
}
