package com.universaldatatools.tools.importer.application.configuration;

import com.universaldatatools.tools.importer.domain.transformation.TransformationConfig;
import com.universaldatatools.tools.importer.domain.transformation.TransformationConfigValidator;
import com.universaldatatools.tools.importer.domain.validation.ValidationConfig;
import com.universaldatatools.tools.importer.domain.validation.ValidationConfigCheck;
import com.universaldatatools.tools.importer.domain.validation.ValidationConfigValidator;
import com.universaldatatools.tools.importer.application.common.SessionLocks;
import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.tools.importer.domain.config.ConfigChange;
import com.universaldatatools.tools.importer.domain.config.ConfigHasher;
import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.config.ImportConfigurationRepository;
import com.universaldatatools.tools.importer.domain.config.Readiness;
import com.universaldatatools.tools.importer.domain.config.ReadinessEvaluator;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.ImportSessionRepository;
import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;
import com.universaldatatools.tools.importer.domain.mapping.MappingConfig;
import com.universaldatatools.tools.importer.domain.mapping.MappingSpec;
import com.universaldatatools.tools.importer.domain.pipeline.ResultStore;
import com.universaldatatools.tools.importer.domain.schema.FieldSpec;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

/** The configuration PUTs. They all share one update flow (design S6); each passes its own change. */
@Service
public class ConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);

    private final ImportSessionRepository sessions;
    private final ImportConfigurationRepository configurations;
    private final ConfigHasher hasher;
    private final SessionLocks locks;
    private final TransactionTemplate transactions;
    private final TransformationConfigValidator transformationValidator;
    private final ValidationConfigValidator validationValidator;
    private final ResultStore results;
    private final Clock clock;
    private final ReadinessEvaluator readiness = ReadinessEvaluator.standard();

    public ConfigurationService(ImportSessionRepository sessions, ImportConfigurationRepository configurations,
                                ConfigHasher hasher, SessionLocks locks, TransactionTemplate transactions,
                                TransformationConfigValidator transformationValidator,
                                ValidationConfigValidator validationValidator, ResultStore results, Clock clock) {
        this.sessions = sessions;
        this.configurations = configurations;
        this.hasher = hasher;
        this.locks = locks;
        this.transactions = transactions;
        this.transformationValidator = transformationValidator;
        this.validationValidator = validationValidator;
        this.results = results;
        this.clock = clock;
    }

    /** Replaces the whole target schema (spec: target-schema). */
    public ConfigUpdateResult updateSchema(UUID sessionId, List<FieldSpec> fields) {
        return update(sessionId, (session, configuration) -> configuration.withSchema(TargetSchema.define(fields)));
    }

    /** Replaces the whole mapping, checked against the current schema and the file's columns (spec: field-mapping). */
    public ConfigUpdateResult updateMapping(UUID sessionId, List<MappingSpec> mappings) {
        return update(sessionId, (session, configuration) -> configuration.withMapping(MappingConfig.define(
                mappings, configuration.schema(), session.sourceSchema().orElseThrow(() -> new IllegalStateException(
                        "Session " + sessionId + " is past UPLOADED without a source schema")))));
    }

    /**
     * Replaces every transformation step, checked against the current schema (spec: transformation). Every
     * problem is reported at once and nothing is stored when there is one.
     */
    public ConfigUpdateResult updateTransformations(UUID sessionId, TransformationConfig transformations) {
        return update(sessionId, (session, configuration) -> {
            List<ProblemItem> problems = transformationValidator.validate(transformations, configuration.schema());
            if (!problems.isEmpty()) {
                throw new DomainException(ErrorCode.CONFIG_INVALID, "Transformation configuration is invalid.", problems);
            }
            return configuration.withTransformations(transformations);
        });
    }

    /**
     * Replaces the user's validation rules, checked against the current schema (spec: validation). Rules the schema
     * already implies are dropped with a {@code RULE_IMPLIED_BY_SCHEMA} warning; any error rejects the whole list.
     */
    public ConfigUpdateResult updateValidations(UUID sessionId, ValidationConfig validations) {
        return update(sessionId, (session, configuration) -> {
            ValidationConfigCheck check = validationValidator.check(validations, configuration.schema());
            if (!check.errors().isEmpty()) {
                throw new DomainException(ErrorCode.CONFIG_INVALID, "Validation configuration is invalid.", check.errors());
            }
            return configuration.withValidations(check.effective(), check.warnings());
        });
    }

    /**
     * Applies {@code mutation} to the stored configuration and moves the session to READY or CONFIGURING.
     * The lock wraps the transaction, so the next write on this session sees this one committed. A rejected
     * change rolls back, leaving both the session and its configuration as they were.
     * <p>
     * A real change makes any stored result stale: it is deleted once the change has committed, still inside the
     * lock (BE-F08 P7). Deleting before the commit could lose a result that stays valid if the commit then fails.
     * Whatever the status: a result may outlive the PROCESSED status, a run that could not save its session say.
     * The deletion is best effort, as the change is already committed: a result that stays behind is never served,
     * since its configuration hash no longer matches (BE-F09).
     */
    private ConfigUpdateResult update(UUID sessionId,
                                      BiFunction<ImportSession, ImportConfiguration, ConfigChange> mutation) {
        return locks.withLock(sessionId, () -> {
            Outcome outcome = transactions.execute(status -> applyAndSave(sessionId, mutation));
            if (outcome.staleResult()) {
                try {
                    results.delete(sessionId);
                } catch (RuntimeException e) {
                    log.warn("Stale result of session {} could not be deleted: {}", sessionId, e.getClass().getName());
                }
            }
            return outcome.result();
        });
    }

    /** The transactional part of {@link #update}. */
    private Outcome applyAndSave(UUID sessionId, BiFunction<ImportSession, ImportConfiguration, ConfigChange> mutation) {
        ImportSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.SESSION_NOT_FOUND, "Import session not found."));
        requireConfigurable(session);
        ImportConfiguration current = configurations.findBySessionId(sessionId)
                .orElseGet(() -> ImportConfiguration.empty(sessionId));
        // Past the state check, the file has been inspected, so the session has its source schema.
        ConfigChange change = mutation.apply(session, current);
        boolean changed = !hasher.hash(current).equals(hasher.hash(change.configuration()));
        Readiness newReadiness = readiness.evaluate(change.configuration());
        Instant now = now();
        // Re-sending the configuration a processed session already has keeps its result valid.
        boolean staleResult = changed;
        if (session.status() != SessionStatus.PROCESSED || changed) {
            session.transitionTo(newReadiness.ready() ? SessionStatus.READY : SessionStatus.CONFIGURING, now);
        }
        ImportConfiguration saved = configurations.save(change.configuration(), now);
        return new Outcome(new ConfigUpdateResult(sessions.save(session), saved, newReadiness, change.warnings()),
                staleResult);
    }

    private record Outcome(ConfigUpdateResult result, boolean staleResult) {
    }

    private static void requireConfigurable(ImportSession session) {
        switch (session.status()) {
            case UPLOADED -> throw new DomainException(ErrorCode.SESSION_STATE_INVALID,
                    "Source file has not been inspected.");
            case FAILED -> throw new DomainException(ErrorCode.SESSION_STATE_INVALID,
                    "Session has failed and cannot be changed.");
            case CONFIGURING, READY, PROCESSED -> {
            }
        }
    }

    /** PostgreSQL keeps microseconds; truncating here keeps memory and database in agreement. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
