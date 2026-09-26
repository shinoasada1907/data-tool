package com.universalimporter.application.configuration;

import com.universalimporter.application.common.SessionLocks;
import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.SessionStatus;
import com.universalimporter.domain.importsession.SourceFile;
import com.universalimporter.domain.importsession.SourceFileType;
import com.universalimporter.domain.mapping.MappingSpec;
import com.universalimporter.domain.pipeline.SampleDataset;
import com.universalimporter.domain.transformation.TransformationConfig;
import com.universalimporter.domain.transformation.TransformationConfigValidator;
import com.universalimporter.domain.transformation.TransformationRegistry;
import com.universalimporter.domain.transformation.TransformationStep;
import com.universalimporter.domain.validation.ValidationConfigValidator;
import com.universalimporter.infrastructure.persistence.JsonConfigHasher;
import com.universalimporter.support.InMemoryImportConfigurationRepository;
import com.universalimporter.support.InMemoryImportSessionRepository;
import com.universalimporter.support.InMemoryResultStore;
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
        sessions.save(ImportSession.restore(ID, new SourceFile("customers.csv", SourceFileType.CSV, 100), status,
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
