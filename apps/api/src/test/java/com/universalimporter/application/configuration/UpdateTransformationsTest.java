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
import com.universalimporter.domain.transformation.TransformationConfig;
import com.universalimporter.domain.transformation.TransformationConfigValidator;
import com.universalimporter.domain.transformation.TransformationRegistry;
import com.universalimporter.domain.transformation.TransformationStep;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class UpdateTransformationsTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final Instant T0 = Instant.parse("2026-09-26T09:00:00Z");
    private static final TargetSchema SCHEMA = TargetSchema.define(List.of(
            new FieldSpec("name", "string", false, 0), new FieldSpec("dob", "date", false, 1)));

    private final InMemoryImportSessionRepository sessions = new InMemoryImportSessionRepository();
    private final InMemoryImportConfigurationRepository configurations = new InMemoryImportConfigurationRepository();
    private final ConfigurationService service = new ConfigurationService(sessions, configurations,
            configuration -> configuration.toString(), new SessionLocks(), new TransactionTemplate(new NoDatabase()),
            new TransformationConfigValidator(TransformationRegistry.standard()),
            new com.universalimporter.domain.validation.ValidationConfigValidator(),
            new com.universalimporter.support.InMemoryResultStore(),
            Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void a_valid_configuration_is_stored_in_canonical_order() {
        given(SessionStatus.CONFIGURING);

        ConfigUpdateResult result = service.updateTransformations(ID, config(
                new TransformationStep("dob", 0, "dateFormat", Map.of("inputFormat", "dd/MM/yyyy")),
                step("name", 1, "uppercase"), step("name", 0, "trim")));

        assertThat(result.warnings()).isEmpty();
        assertThat(configurations.findBySessionId(ID).orElseThrow().transformations().transformations())
                .extracting(TransformationStep::targetField, TransformationStep::type)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("name", "trim"),
                        org.assertj.core.groups.Tuple.tuple("name", "uppercase"),
                        org.assertj.core.groups.Tuple.tuple("dob", "dateFormat"));
    }

    @Test
    void every_problem_is_reported_and_nothing_is_stored() {
        given(SessionStatus.CONFIGURING);
        ImportConfiguration before = configurations.findBySessionId(ID).orElseThrow();

        DomainException ex = catchThrowableOfType(DomainException.class, () -> service.updateTransformations(ID,
                config(step("name", 0, "replace"), step("phone", 0, "trim"))));

        assertThat(ex.code()).isEqualTo(ErrorCode.CONFIG_INVALID);
        assertThat(ex.getMessage()).isEqualTo("Transformation configuration is invalid.");
        assertThat(ex.items()).hasSize(2);
        assertThat(configurations.findBySessionId(ID)).contains(before);
    }

    @Test
    void a_failed_session_cannot_be_changed() {
        given(SessionStatus.FAILED);
        ImportConfiguration before = configurations.findBySessionId(ID).orElseThrow();

        DomainException ex = catchThrowableOfType(DomainException.class,
                () -> service.updateTransformations(ID, config(step("name", 0, "trim"))));

        assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_STATE_INVALID);
        assertThat(configurations.findBySessionId(ID)).contains(before);
    }

    @Test
    void an_unknown_session_is_not_found() {
        DomainException ex = catchThrowableOfType(DomainException.class,
                () -> service.updateTransformations(ID, config(step("name", 0, "trim"))));

        assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void transformations_do_not_change_readiness() {
        given(SessionStatus.READY);

        ConfigUpdateResult result = service.updateTransformations(ID, config(step("name", 0, "trim")));

        assertThat(result.session().status()).isEqualTo(SessionStatus.READY);
        assertThat(result.readiness().ready()).isTrue();
    }

    private void given(SessionStatus status) {
        sessions.save(ImportSession.restore(ID, new SourceFile("customers.csv", SourceFileType.CSV, 20), status,
                T0, T0, 0L, new SourceSchema(List.of(new SourceColumn(0, "name"), new SourceColumn(1, "dob")), 1, null)));
        configurations.save(ImportConfiguration.empty(ID).withSchema(SCHEMA).configuration(), T0);
    }

    private static TransformationConfig config(TransformationStep... steps) {
        return new TransformationConfig(List.of(steps));
    }

    private static TransformationStep step(String field, int order, String type) {
        return new TransformationStep(field, order, type, null);
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
