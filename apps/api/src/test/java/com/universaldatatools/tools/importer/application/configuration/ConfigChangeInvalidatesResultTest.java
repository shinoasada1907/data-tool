package com.universaldatatools.tools.importer.application.configuration;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.transform.TransformationRegistry;
import com.universaldatatools.core.transform.TransformationStep;
import com.universaldatatools.support.InMemoryImportConfigurationRepository;
import com.universaldatatools.support.InMemoryImportSessionRepository;
import com.universaldatatools.support.InMemoryResultStore;
import com.universaldatatools.tools.importer.application.common.SessionLocks;
import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;
import com.universaldatatools.tools.importer.domain.importsession.SourceFile;
import com.universaldatatools.tools.importer.domain.mapping.MappingSpec;
import com.universaldatatools.tools.importer.domain.pipeline.SampleDataset;
import com.universaldatatools.tools.importer.domain.transformation.TransformationConfig;
import com.universaldatatools.tools.importer.domain.transformation.TransformationConfigValidator;
import com.universaldatatools.tools.importer.domain.validation.ValidationConfigValidator;
import com.universaldatatools.tools.importer.infrastructure.persistence.JsonConfigHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ConfigChangeInvalidatesResultTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final Instant T0 = Instant.parse("2026-09-26T09:00:00Z");

    private final InMemoryImportSessionRepository sessions = new InMemoryImportSessionRepository();
    private final InMemoryImportConfigurationRepository configurations = new InMemoryImportConfigurationRepository();
    private final InMemoryResultStore results = new InMemoryResultStore();
    private final ConfigurationService service = new ConfigurationService(sessions, configurations, new JsonConfigHasher(),
            new SessionLocks(), new TransactionTemplate(new NoDatabase()),
            new TransformationConfigValidator(TransformationRegistry.standard()), new ValidationConfigValidator(), results,
            Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void processedSampleWithAResult() {
        givenSample(SessionStatus.PROCESSED);
        results.put(ID, new InMemoryResultStore.Stored(null, List.of()));
    }

    @Test
    void a_real_change_deletes_the_result_and_makes_the_session_ready_again() {
        ConfigUpdateResult result = service.updateTransformations(ID, new TransformationConfig(List.of(
                new TransformationStep("name", 0, "uppercase", null))));

        assertThat(results.stored(ID)).isEmpty();
        assertThat(result.session().status()).isEqualTo(SessionStatus.READY);
    }

    @Test
    void re_sending_the_same_configuration_keeps_the_result() {
        ConfigUpdateResult result = service.updateValidations(ID, SampleDataset.VALIDATIONS);

        assertThat(results.stored(ID)).isPresent();
        assertThat(result.session().status()).isEqualTo(SessionStatus.PROCESSED);
    }

    @Test
    void unmapping_a_required_field_deletes_the_result_and_needs_configuring() {
        ConfigUpdateResult result = service.updateMapping(ID, List.of(new MappingSpec("name", "SOURCE_COLUMN", "Họ tên", null)));

        assertThat(results.stored(ID)).isEmpty();
        assertThat(result.session().status()).isEqualTo(SessionStatus.CONFIGURING);
    }

    @Test
    void the_result_is_kept_when_saving_the_change_fails() {
        sessions.failOnSave();

        catchThrowableOfType(IllegalStateException.class, () -> service.updateTransformations(ID,
                new TransformationConfig(List.of(new TransformationStep("name", 0, "uppercase", null)))));

        assertThat(results.stored(ID)).isPresent();
    }

    @Test
    void a_stale_result_is_deleted_whatever_the_session_status() {
        givenSample(SessionStatus.READY);

        service.updateTransformations(ID, new TransformationConfig(List.of(new TransformationStep("name", 0, "uppercase", null))));

        assertThat(results.stored(ID)).isEmpty();
    }

    @Test
    void a_result_that_cannot_be_deleted_does_not_undo_the_committed_change() {
        InMemoryResultStore stuck = new InMemoryResultStore() {
            @Override
            public void delete(UUID sessionId) {
                throw new java.io.UncheckedIOException(new java.io.IOException("file in use"));
            }
        };
        ConfigurationService service = new ConfigurationService(sessions, configurations, new JsonConfigHasher(),
                new SessionLocks(), new TransactionTemplate(new NoDatabase()),
                new TransformationConfigValidator(TransformationRegistry.standard()), new ValidationConfigValidator(),
                stuck, Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC));

        ConfigUpdateResult result = service.updateTransformations(ID, new TransformationConfig(List.of(
                new TransformationStep("name", 0, "uppercase", null))));

        assertThat(result.session().status()).isEqualTo(SessionStatus.READY);
    }

    @Test
    void a_failed_session_changes_nothing() {
        givenSample(SessionStatus.FAILED);
        ImportConfiguration before = configurations.findBySessionId(ID).orElseThrow();

        DomainException ex = catchThrowableOfType(DomainException.class, () -> service.updateTransformations(ID,
                new TransformationConfig(List.of(new TransformationStep("name", 0, "uppercase", null)))));

        assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_STATE_INVALID);
        assertThat(configurations.findBySessionId(ID)).contains(before);
        assertThat(results.stored(ID)).isPresent();
    }

    private void givenSample(SessionStatus status) {
        sessions.save(ImportSession.restore(ID, new SourceFile("customers.csv", DataFormat.CSV, 100), status,
                T0, T0, 0L, SampleDataset.SOURCE));
        configurations.save(ImportConfiguration.empty(ID).withSchema(SampleDataset.SCHEMA).configuration()
                .withMapping(SampleDataset.MAPPING).configuration()
                .withTransformations(SampleDataset.TRANSFORMATIONS).configuration()
                .withValidations(SampleDataset.VALIDATIONS, List.of()).configuration(), T0);
    }

    /** Runs the callback without a database. */
    private static final class NoDatabase implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
