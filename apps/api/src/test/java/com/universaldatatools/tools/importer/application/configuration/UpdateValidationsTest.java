package com.universaldatatools.tools.importer.application.configuration;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.SourceSchema;
import com.universaldatatools.core.transform.TransformationRegistry;
import com.universaldatatools.support.InMemoryImportConfigurationRepository;
import com.universaldatatools.support.InMemoryImportSessionRepository;
import com.universaldatatools.tools.importer.application.common.SessionLocks;
import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;
import com.universaldatatools.tools.importer.domain.importsession.SourceFile;
import com.universaldatatools.tools.importer.domain.schema.FieldSpec;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;
import com.universaldatatools.tools.importer.domain.transformation.TransformationConfigValidator;
import com.universaldatatools.tools.importer.domain.validation.ValidationConfig;
import com.universaldatatools.tools.importer.domain.validation.ValidationConfigValidator;
import com.universaldatatools.tools.importer.domain.validation.ValidationRuleConfig;
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

class UpdateValidationsTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final Instant T0 = Instant.parse("2026-09-26T09:00:00Z");
    private static final TargetSchema SCHEMA = TargetSchema.define(List.of(
            new FieldSpec("name", "string", false, 0), new FieldSpec("email", "email", false, 1),
            new FieldSpec("age", "number", false, 2)));

    private final InMemoryImportSessionRepository sessions = new InMemoryImportSessionRepository();
    private final InMemoryImportConfigurationRepository configurations = new InMemoryImportConfigurationRepository();
    private final ConfigurationService service = new ConfigurationService(sessions, configurations,
            configuration -> configuration.toString(), new SessionLocks(), new TransactionTemplate(new NoDatabase()),
            new TransformationConfigValidator(TransformationRegistry.standard()), new ValidationConfigValidator(),
            new com.universaldatatools.support.InMemoryResultStore(),
            Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void rules_implied_by_the_schema_are_dropped_with_a_warning_and_the_rest_stored() {
        given(SessionStatus.CONFIGURING);

        ConfigUpdateResult result = service.updateValidations(ID, config(rule("email", "unique"), rule("name", "required")));

        assertThat(result.warnings()).extracting(ProblemItem::field, ProblemItem::code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("name", "RULE_IMPLIED_BY_SCHEMA"));
        assertThat(configurations.findBySessionId(ID).orElseThrow().validations().validations())
                .containsExactly(rule("email", "unique"));
    }

    @Test
    void an_invalid_configuration_is_rejected_and_nothing_is_stored() {
        given(SessionStatus.CONFIGURING);
        ImportConfiguration before = configurations.findBySessionId(ID).orElseThrow();

        DomainException ex = catchThrowableOfType(DomainException.class,
                () -> service.updateValidations(ID, config(rule("age", "email"))));

        assertThat(ex.code()).isEqualTo(ErrorCode.CONFIG_INVALID);
        assertThat(ex.getMessage()).isEqualTo("Validation configuration is invalid.");
        assertThat(ex.items()).hasSize(1);
        assertThat(configurations.findBySessionId(ID)).contains(before);
    }

    @Test
    void a_failed_session_cannot_be_changed() {
        given(SessionStatus.FAILED);

        DomainException ex = catchThrowableOfType(DomainException.class,
                () -> service.updateValidations(ID, config(rule("email", "unique"))));

        assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_STATE_INVALID);
    }

    @Test
    void an_unknown_session_is_not_found() {
        DomainException ex = catchThrowableOfType(DomainException.class,
                () -> service.updateValidations(ID, config(rule("email", "unique"))));

        assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }

    private void given(SessionStatus status) {
        sessions.save(ImportSession.restore(ID, new SourceFile("customers.csv", DataFormat.CSV, 20), status,
                T0, T0, 0L, new SourceSchema(List.of(new Column(0, "name")), 1, null)));
        configurations.save(ImportConfiguration.empty(ID).withSchema(SCHEMA).configuration(), T0);
    }

    private static ValidationConfig config(ValidationRuleConfig... rules) {
        return new ValidationConfig(List.of(rules));
    }

    private static ValidationRuleConfig rule(String field, String type) {
        return new ValidationRuleConfig(field, type, null);
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
