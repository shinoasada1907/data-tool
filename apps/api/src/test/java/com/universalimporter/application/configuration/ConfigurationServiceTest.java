package com.universalimporter.application.configuration;

import com.universalimporter.application.common.SessionLocks;
import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.SessionStatus;
import com.universalimporter.domain.importsession.SourceFile;
import com.universalimporter.domain.importsession.SourceFileType;
import com.universalimporter.domain.schema.FieldSpec;
import com.universalimporter.domain.schema.TargetSchema;
import com.universalimporter.domain.source.SourceColumn;
import com.universalimporter.domain.source.SourceSchema;
import com.universalimporter.support.InMemoryImportConfigurationRepository;
import com.universalimporter.support.InMemoryImportSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ConfigurationServiceTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final Instant T0 = Instant.parse("2026-09-26T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00.123456789Z");
    private static final Instant NOW_IN_MICROS = Instant.parse("2026-09-26T10:00:00.123456Z");
    private static final SourceSchema SOURCE = new SourceSchema(List.of(new SourceColumn(0, "note")), 1, null);
    private static final List<FieldSpec> NOTE = List.of(new FieldSpec("note", "string", false, 0));

    private final InMemoryImportSessionRepository sessions = new InMemoryImportSessionRepository();
    private final InMemoryImportConfigurationRepository configurations = new InMemoryImportConfigurationRepository();
    private final RecordingSessionLocks locks = new RecordingSessionLocks();
    private final RecordingTransactionManager transactionManager = new RecordingTransactionManager(locks);
    private final ConfigurationService service = new ConfigurationService(sessions, configurations,
            // The hash only has to tell different content apart.
            configuration -> configuration.schema().toString(),
            locks, new TransactionTemplate(transactionManager), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void a_valid_schema_makes_a_configuring_session_ready() {
        givenSession(SessionStatus.CONFIGURING);

        ConfigUpdateResult result = service.updateSchema(ID, NOTE);

        assertThat(result.session().status()).isEqualTo(SessionStatus.READY);
        assertThat(result.session().updatedAt()).isEqualTo(NOW_IN_MICROS);
        assertThat(result.readiness().ready()).isTrue();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.configuration().schema()).isEqualTo(TargetSchema.define(NOTE));
        assertThat(configurations.findBySessionId(ID).orElseThrow().schema()).isEqualTo(TargetSchema.define(NOTE));
        assertThat(sessions.findById(ID).orElseThrow().status()).isEqualTo(SessionStatus.READY);
        assertThat(transactionManager.commits).isEqualTo(1);
    }

    @Test
    void an_invalid_schema_changes_nothing() {
        givenSession(SessionStatus.READY);
        ImportConfiguration before = configurations.save(
                ImportConfiguration.empty(ID).withSchema(TargetSchema.define(NOTE)).configuration(), T0);

        DomainException ex = catchThrowableOfType(DomainException.class, () -> service.updateSchema(ID, List.of()));

        assertThat(ex.code()).isEqualTo(ErrorCode.SCHEMA_INVALID);
        assertThat(sessions.findById(ID).orElseThrow().status()).isEqualTo(SessionStatus.READY);
        assertThat(configurations.findBySessionId(ID)).contains(before);
        assertThat(transactionManager.rollbacks).isEqualTo(1);
    }

    @Test
    void an_uninspected_session_cannot_be_configured() {
        givenSession(SessionStatus.UPLOADED);

        DomainException ex = catchThrowableOfType(DomainException.class, () -> service.updateSchema(ID, NOTE));

        assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_STATE_INVALID);
        assertThat(ex.getMessage()).isEqualTo("Source file has not been inspected.");
        assertThat(configurations.findBySessionId(ID)).isEmpty();
    }

    @Test
    void a_failed_session_cannot_be_configured() {
        givenSession(SessionStatus.FAILED);

        DomainException ex = catchThrowableOfType(DomainException.class, () -> service.updateSchema(ID, NOTE));

        assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_STATE_INVALID);
        assertThat(ex.getMessage()).isEqualTo("Session has failed and cannot be changed.");
        assertThat(configurations.findBySessionId(ID)).isEmpty();
    }

    @Test
    void an_unknown_session_is_not_found() {
        DomainException ex = catchThrowableOfType(DomainException.class, () -> service.updateSchema(ID, NOTE));

        assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void a_processed_session_stays_processed_when_the_configuration_does_not_change() {
        givenSession(SessionStatus.PROCESSED);
        configurations.save(ImportConfiguration.empty(ID).withSchema(TargetSchema.define(NOTE)).configuration(), T0);

        ConfigUpdateResult result = service.updateSchema(ID, NOTE);

        assertThat(result.session().status()).isEqualTo(SessionStatus.PROCESSED);
        assertThat(result.session().updatedAt()).isEqualTo(T0);
    }

    @Test
    void a_processed_session_is_ready_again_after_a_real_change() {
        givenSession(SessionStatus.PROCESSED);
        configurations.save(ImportConfiguration.empty(ID).withSchema(TargetSchema.define(NOTE)).configuration(), T0);

        ConfigUpdateResult result = service.updateSchema(ID, List.of(new FieldSpec("email", "email", true, 0)));

        assertThat(result.session().status()).isEqualTo(SessionStatus.READY);
        assertThat(result.session().updatedAt()).isEqualTo(NOW_IN_MICROS);
    }

    @Test
    void the_whole_transaction_runs_inside_the_session_lock() {
        givenSession(SessionStatus.CONFIGURING);

        service.updateSchema(ID, NOTE);
        catchThrowableOfType(DomainException.class, () -> service.updateSchema(ID, List.of()));

        assertThat(locks.lockedIds).containsExactly(ID, ID);
        assertThat(transactionManager.startedInsideLock).containsExactly(true, true);
    }

    private void givenSession(SessionStatus status) {
        sessions.save(ImportSession.restore(ID, new SourceFile("notes.csv", SourceFileType.CSV, 10), status,
                T0, T0, 0L, status == SessionStatus.UPLOADED ? null : SOURCE));
    }

    /** Remembers which sessions were locked and whether a lock is held right now. */
    static class RecordingSessionLocks extends SessionLocks {

        final List<UUID> lockedIds = new ArrayList<>();
        boolean held;

        @Override
        public <T> T withLock(UUID sessionId, Supplier<T> action) {
            lockedIds.add(sessionId);
            return super.withLock(sessionId, () -> {
                held = true;
                try {
                    return action.get();
                } finally {
                    held = false;
                }
            });
        }
    }

    /** Runs the callback without a database; counts commits and rollbacks. */
    static class RecordingTransactionManager implements PlatformTransactionManager {

        private final RecordingSessionLocks locks;
        final List<Boolean> startedInsideLock = new ArrayList<>();
        int commits;
        int rollbacks;

        RecordingTransactionManager(RecordingSessionLocks locks) {
            this.locks = locks;
        }

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            startedInsideLock.add(locks.held);
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            commits++;
        }

        @Override
        public void rollback(TransactionStatus status) {
            rollbacks++;
        }
    }
}
