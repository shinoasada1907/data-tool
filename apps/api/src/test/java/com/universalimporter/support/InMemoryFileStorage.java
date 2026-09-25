package com.universalimporter.support;

import com.universalimporter.domain.importsession.FileStorage;

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
        files.remove(sessionId);
    }
}
