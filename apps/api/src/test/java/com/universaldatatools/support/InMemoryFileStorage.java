package com.universaldatatools.support;

import com.universaldatatools.platform.storage.FileStorage;
import com.universaldatatools.platform.storage.StoredEntry;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Test double for file storage that keeps bytes in memory. */
public class InMemoryFileStorage implements FileStorage {

    private final Map<UUID, byte[]> files = new HashMap<>();
    private final Map<UUID, java.time.Instant> lastModified = new HashMap<>();
    private final java.util.Set<UUID> failDeleteFor = new java.util.HashSet<>();

    /** A session directory with this last change time, holding a file or not (an orphan, say). */
    public void directory(UUID sessionId, java.time.Instant modified) {
        lastModified.put(sessionId, modified);
    }

    /** Deleting these sessions' files fails, as when a file is locked. */
    public void failDeleteFor(UUID... ids) {
        failDeleteFor.addAll(java.util.List.of(ids));
    }

    private UUID owner;

    public void allowDeletes() {
        failDeleteFor.clear();
    }

    @Override
    public java.util.Optional<UUID> owner() {
        return java.util.Optional.ofNullable(owner);
    }

    @Override
    public void claim(UUID installation) {
        this.owner = installation;
    }

    public boolean holds(UUID sessionId) {
        return files.containsKey(sessionId) || lastModified.containsKey(sessionId);
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    public byte[] content(UUID sessionId) {
        return files.get(sessionId);
    }

    @Override
    public long save(UUID sessionId, InputStream content) {
        try {
            byte[] bytes = content.readAllBytes();
            files.put(sessionId, bytes);
            return bytes.length;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public InputStream open(UUID sessionId) {
        byte[] bytes = files.get(sessionId);
        if (bytes == null) {
            throw new UncheckedIOException(new FileNotFoundException(sessionId.toString()));
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void delete(UUID sessionId) {
        if (failDeleteFor.contains(sessionId)) {
            throw new UncheckedIOException(new IOException("file in use"));
        }
        files.remove(sessionId);
        lastModified.remove(sessionId);
    }

    @Override
    public java.util.List<StoredEntry> listEntries() {
        java.util.Set<UUID> ids = new java.util.TreeSet<>(files.keySet());
        ids.addAll(lastModified.keySet());
        return ids.stream().map(id -> new StoredEntry(id,
                lastModified.getOrDefault(id, java.time.Instant.EPOCH))).toList();
    }
}
