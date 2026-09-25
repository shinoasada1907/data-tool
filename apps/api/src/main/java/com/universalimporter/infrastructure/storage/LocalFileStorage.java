package com.universalimporter.infrastructure.storage;

import com.universalimporter.domain.importsession.FileStorage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** Keeps session files under {@code {root}/{sessionId}/} on the local disk (design D6). */
@Component
public class LocalFileStorage implements FileStorage {

    private static final String SOURCE_FILE = "source.bin";

    private final Path root;

    public LocalFileStorage(StorageProperties properties) {
        this.root = properties.dir().toAbsolutePath().normalize();
    }

    @Override
    public long save(UUID sessionId, InputStream content) {
        Path sessionDir = sessionDir(sessionId);
        try {
            Files.createDirectories(sessionDir);
            // Write next to the target, then move: readers never see a half-written file.
            Path temp = Files.createTempFile(sessionDir, SOURCE_FILE, ".tmp");
            try {
                long size = Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp, sessionDir.resolve(SOURCE_FILE),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                return size;
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store the source file of session " + sessionId, e);
        }
    }

    @Override
    public InputStream open(UUID sessionId) {
        try {
            return Files.newInputStream(sessionDir(sessionId).resolve(SOURCE_FILE));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open the source file of session " + sessionId, e);
        }
    }

    @Override
    public void delete(UUID sessionId) {
        Path sessionDir = sessionDir(sessionId);
        if (Files.notExists(sessionDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(sessionDir)) {
            List<Path> deepestFirst = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : deepestFirst) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete the files of session " + sessionId, e);
        }
    }

    private Path sessionDir(UUID sessionId) {
        return root.resolve(sessionId.toString());
    }
}
