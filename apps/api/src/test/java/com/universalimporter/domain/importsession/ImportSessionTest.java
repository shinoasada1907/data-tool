package com.universalimporter.domain.importsession;

import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.source.SourceColumn;
import com.universalimporter.domain.source.SourceSchema;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportSessionTest {

    private static final Instant T0 = Instant.parse("2026-09-25T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-09-25T10:01:00Z");
    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final SourceFile FILE = new SourceFile("customers.csv", SourceFileType.CSV, 7);
    private static final SourceSchema SCHEMA =
            new SourceSchema(List.of(new SourceColumn(0, "name"), new SourceColumn(1, "email")), 2, null);

    @Test
    void new_session_starts_uploaded_with_equal_timestamps() {
        ImportSession session = ImportSession.create(ID, FILE, T0);

        assertThat(session.id()).isEqualTo(ID);
        assertThat(session.sourceFile()).isEqualTo(FILE);
        assertThat(session.status()).isEqualTo(SessionStatus.UPLOADED);
        assertThat(session.createdAt()).isEqualTo(T0);
        assertThat(session.updatedAt()).isEqualTo(T0);
        assertThat(session.version()).isNull();
    }

    @Test
    void allowed_transition_moves_status_and_touches_updated_at() {
        ImportSession session = ImportSession.restore(ID, FILE, SessionStatus.READY, T0, T0, 3L, null);

        session.transitionTo(SessionStatus.PROCESSED, T1);

        assertThat(session.status()).isEqualTo(SessionStatus.PROCESSED);
        assertThat(session.updatedAt()).isEqualTo(T1);
        assertThat(session.createdAt()).isEqualTo(T0);
        assertThat(session.version()).isEqualTo(3L);
    }

    @Test
    void failed_is_terminal() {
        ImportSession session = ImportSession.restore(ID, FILE, SessionStatus.FAILED, T0, T0, 3L, null);

        assertThatThrownBy(() -> session.transitionTo(SessionStatus.CONFIGURING, T1))
                .isInstanceOfSatisfying(DomainException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_STATE_INVALID));
        assertThat(session.status()).isEqualTo(SessionStatus.FAILED);
        assertThat(session.updatedAt()).isEqualTo(T0);
    }

    @Test
    void a_new_session_has_no_source_schema() {
        assertThat(ImportSession.create(ID, FILE, T0).sourceSchema()).isEmpty();
    }

    @Test
    void inspecting_an_uploaded_session_stores_the_schema_and_moves_it_to_configuring() {
        ImportSession session = ImportSession.create(ID, FILE, T0);

        session.markInspected(SCHEMA, T1);

        assertThat(session.status()).isEqualTo(SessionStatus.CONFIGURING);
        assertThat(session.sourceSchema()).contains(SCHEMA);
        assertThat(session.updatedAt()).isEqualTo(T1);
    }

    @Test
    void a_session_is_inspected_only_once() {
        // READY → CONFIGURING is a valid transition, yet inspecting again must still be refused.
        ImportSession session = ImportSession.restore(ID, FILE, SessionStatus.READY, T0, T0, 3L, SCHEMA);

        assertThatThrownBy(() -> session.markInspected(SCHEMA, T1))
                .isInstanceOfSatisfying(DomainException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_STATE_INVALID);
                    assertThat(ex.getMessage()).isEqualTo("Source file has already been inspected.");
                });
        assertThat(session.status()).isEqualTo(SessionStatus.READY);
    }

    @Test
    void cannot_skip_from_uploaded_to_processed() {
        ImportSession session = ImportSession.create(ID, FILE, T0);

        assertThatThrownBy(() -> session.transitionTo(SessionStatus.PROCESSED, T1))
                .isInstanceOfSatisfying(DomainException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_STATE_INVALID));
        assertThat(session.status()).isEqualTo(SessionStatus.UPLOADED);
    }
}
