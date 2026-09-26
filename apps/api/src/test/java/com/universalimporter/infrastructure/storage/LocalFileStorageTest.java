package com.universalimporter.infrastructure.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.universalimporter.domain.importsession.StoredEntry;
import com.universalimporter.support.Junctions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");

    @TempDir
    Path root;

    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage(new StorageProperties(root));
    }

    @Test
    void save_writes_only_source_bin_under_the_session_directory_and_returns_its_size() throws IOException {
        long written = storage.save(ID, stream("a,b"));

        Path sessionDir = root.resolve(ID.toString());
        assertThat(written).isEqualTo(3);
        assertThat(Files.readString(sessionDir.resolve("source.bin"))).isEqualTo("a,b");
        try (Stream<Path> files = Files.list(sessionDir)) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly("source.bin");
        }
    }

    @Test
    void open_returns_the_saved_bytes() throws IOException {
        storage.save(ID, stream("a,b"));

        try (InputStream in = storage.open(ID)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("a,b");
        }
    }

    @Test
    void delete_removes_the_whole_session_directory_and_can_be_repeated() throws IOException {
        storage.save(ID, stream("a,b"));
        Path resultDir = Files.createDirectories(root.resolve(ID.toString()).resolve("result"));
        Files.writeString(resultDir.resolve("summary.json"), "{}");

        storage.delete(ID);

        assertThat(root.resolve(ID.toString())).doesNotExist();
        assertThatCode(() -> storage.delete(ID)).doesNotThrowAnyException();
    }

    @Test
    void open_of_a_session_without_a_file_fails() {
        assertThatThrownBy(() -> storage.open(UUID.fromString("11111111-2222-3333-4444-555555555555")))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void creates_a_missing_root_directory_on_first_save() {
        Path missingRoot = root.resolve("does").resolve("not").resolve("exist");
        LocalFileStorage fresh = new LocalFileStorage(new StorageProperties(missingRoot));

        fresh.save(ID, stream("x"));

        assertThat(missingRoot.resolve(ID.toString()).resolve("source.bin")).hasContent("x");
    }

    @Test
    void only_session_directories_are_listed_with_their_last_change() throws IOException {
        UUID u1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
        Path d1 = Files.createDirectories(root.resolve(u1.toString()));
        Path d2 = Files.createDirectories(root.resolve(u2.toString()));
        Files.setLastModifiedTime(d1, FileTime.from(Instant.parse("2026-09-01T00:00:00Z")));
        Files.createDirectories(root.resolve("backup"));
        Files.writeString(root.resolve("x.txt"), "x");
        Files.writeString(root.resolve("cccccccc-0000-0000-0000-000000000003"), "a file named like a session");

        List<StoredEntry> entries = storage.listEntries();

        assertThat(entries).containsExactlyInAnyOrder(
                new StoredEntry(u1, Instant.parse("2026-09-01T00:00:00Z")),
                new StoredEntry(u2, Files.getLastModifiedTime(d2).toInstant()));
    }

    @Test
    void the_owner_is_whatever_was_claimed_and_is_no_session() {
        UUID installation = UUID.fromString("99999999-0000-0000-0000-000000000009");
        LocalFileStorage fresh = new LocalFileStorage(new StorageProperties(root.resolve("new-root")));

        assertThat(fresh.owner()).isEmpty();
        fresh.claim(installation);

        assertThat(fresh.owner()).contains(installation);
        assertThat(fresh.listEntries()).isEmpty();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void a_junction_named_like_a_session_is_never_listed() throws Exception {
        Path outside = Files.createDirectories(root.getParent().resolve(root.getFileName() + "-outside"));
        Junctions.create(root.resolve("aaaaaaaa-0000-0000-0000-000000000001"), outside);

        assertThat(storage.listEntries()).isEmpty();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void deleting_a_session_removes_a_junction_inside_it_but_never_what_it_points_to() throws Exception {
        Path outside = Files.createDirectories(root.getParent().resolve(root.getFileName() + "-outside2"));
        Files.writeString(outside.resolve("keep.txt"), "not ours");
        storage.save(ID, stream("a,b"));
        Junctions.create(root.resolve(ID.toString()).resolve("peek"), outside);

        storage.delete(ID);

        assertThat(root.resolve(ID.toString())).doesNotExist();
        assertThat(outside.resolve("keep.txt")).hasContent("not ours");
    }

    @Test
    void a_missing_root_lists_nothing() {
        LocalFileStorage fresh = new LocalFileStorage(new StorageProperties(root.resolve("missing")));

        assertThat(fresh.listEntries()).isEmpty();
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
