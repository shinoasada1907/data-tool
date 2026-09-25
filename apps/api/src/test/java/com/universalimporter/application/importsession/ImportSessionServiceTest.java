package com.universalimporter.application.importsession;

import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.SessionStatus;
import com.universalimporter.domain.importsession.SourceFileType;
import com.universalimporter.support.InMemoryFileStorage;
import com.universalimporter.support.InMemoryImportSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportSessionServiceTest {

    /** The clock ticks in nanoseconds; stored timestamps keep microseconds, as PostgreSQL does. */
    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00.123456789Z");
    private static final Instant NOW_IN_MICROS = Instant.parse("2026-09-25T10:00:00.123456Z");

    private final InMemoryImportSessionRepository repository = new InMemoryImportSessionRepository();
    private final InMemoryFileStorage storage = new InMemoryFileStorage();
    private final ImportSessionService service =
            new ImportSessionService(repository, storage, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void upload_stores_the_file_and_creates_an_uploaded_session() {
        ImportSession session = service.upload("customers.csv", content("a,b\n1,2"));

        assertThat(session.status()).isEqualTo(SessionStatus.UPLOADED);
        assertThat(session.sourceFile().fileType()).isEqualTo(SourceFileType.CSV);
        assertThat(session.sourceFile().sizeBytes()).isEqualTo(7);
        assertThat(session.sourceFile().originalFileName()).isEqualTo("customers.csv");
        assertThat(session.createdAt()).isEqualTo(NOW_IN_MICROS);
        assertThat(storage.content(session.id())).isEqualTo("a,b\n1,2".getBytes(StandardCharsets.UTF_8));
        assertThat(repository.findById(session.id())).isPresent();
    }

    @Test
    void upload_keeps_only_the_sanitized_base_name() {
        ImportSession session = service.upload("../x.csv", content("a"));

        assertThat(session.sourceFile().originalFileName()).isEqualTo("x.csv");
    }

    @Test
    void unsupported_file_is_rejected_before_anything_is_stored() {
        assertThatThrownBy(() -> service.upload("data.xls", content("a")))
                .isInstanceOfSatisfying(DomainException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.FILE_UNSUPPORTED));
        assertThat(storage.isEmpty()).isTrue();
        assertThat(repository.isEmpty()).isTrue();
    }

    @Test
    void empty_file_is_rejected_before_anything_is_stored() {
        assertThatThrownBy(() -> service.upload("e.csv", content("")))
                .isInstanceOfSatisfying(DomainException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.FILE_EMPTY));
        assertThat(storage.isEmpty()).isTrue();
        assertThat(repository.isEmpty()).isTrue();
    }

    @Test
    void stored_file_is_removed_when_the_session_cannot_be_saved() {
        repository.failOnSave();

        assertThatThrownBy(() -> service.upload("a.csv", content("a")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database is down");
        assertThat(storage.isEmpty()).isTrue();
    }

    @Test
    void get_returns_the_stored_session() {
        ImportSession uploaded = service.upload("customers.csv", content("a,b"));

        ImportSession found = service.get(uploaded.id());

        assertThat(found.id()).isEqualTo(uploaded.id());
        assertThat(found.status()).isEqualTo(SessionStatus.UPLOADED);
    }

    @Test
    void get_of_an_unknown_id_is_session_not_found() {
        assertThatThrownBy(() -> service.get(UUID.fromString("11111111-2222-3333-4444-555555555555")))
                .isInstanceOfSatisfying(DomainException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_NOT_FOUND));
    }

    private static ByteArrayResource content(String text) {
        return new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8));
    }
}
