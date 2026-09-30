package com.universaldatatools.platform.storage;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/** Keeps session files under {@code {root}/{sessionId}/} on the local disk (design D6). */
@Component
public class LocalFileStorage implements FileStorage {

    private static final String SOURCE_FILE = "source.bin";
    /** Not a session id, so the cleanup never lists nor deletes it. */
    private static final String OWNER_FILE = ".owner";

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
        try {
            FileTrees.deleteTree(sessionDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete the files of session " + sessionId, e);
        }
    }

    /** Only directories named after a session id; any other name is someone else's and is left alone. */
    @Override
    public List<StoredEntry> listEntries() {
        if (Files.notExists(root)) {
            return List.of();
        }
        List<StoredEntry> entries = new ArrayList<>();
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : children.toList()) {
                Optional<UUID> id = sessionId(child.getFileName().toString());
                BasicFileAttributes attributes =
                        Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                // A link or junction named like a session is someone else's doing: never ours to delete.
                if (id.isPresent() && attributes.isDirectory() && !FileTrees.isLink(attributes)) {
                    entries.add(new StoredEntry(id.get(), attributes.lastModifiedTime().toInstant()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list the storage directory", e);
        }
        return entries;
    }

    private static Optional<UUID> sessionId(String name) {
        try {
            UUID id = UUID.fromString(name);
            // UUID.fromString also accepts short forms such as "1-2-3-4-5"; only the canonical name is a session.
            return id.toString().equals(name) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UUID> owner() {
        Path marker = root.resolve(OWNER_FILE);
        if (Files.notExists(marker)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(Files.readString(marker, StandardCharsets.US_ASCII).strip()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the owner of the storage directory", e);
        }
    }

    @Override
    public void claim(UUID installation) {
        try {
            Files.createDirectories(root);
            Files.writeString(root.resolve(OWNER_FILE), installation + "\n", StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot mark the owner of the storage directory", e);
        }
    }

    private Path sessionDir(UUID sessionId) {
        return root.resolve(sessionId.toString());
    }
}
