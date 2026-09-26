package com.universalimporter.application.configuration;

import com.universalimporter.application.common.SessionLocks;
import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.config.ConfigChange;
import com.universalimporter.domain.config.ConfigHasher;
import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.config.ImportConfigurationRepository;
import com.universalimporter.domain.config.Readiness;
import com.universalimporter.domain.config.ReadinessEvaluator;
import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.ImportSessionRepository;
import com.universalimporter.domain.importsession.SessionStatus;
import com.universalimporter.domain.mapping.MappingConfig;
import com.universalimporter.domain.mapping.MappingSpec;
import com.universalimporter.domain.schema.FieldSpec;
import com.universalimporter.domain.schema.TargetSchema;
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

    private final ImportSessionRepository sessions;
    private final ImportConfigurationRepository configurations;
    private final ConfigHasher hasher;
    private final SessionLocks locks;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final ReadinessEvaluator readiness = ReadinessEvaluator.standard();

    public ConfigurationService(ImportSessionRepository sessions, ImportConfigurationRepository configurations,
                                ConfigHasher hasher, SessionLocks locks, TransactionTemplate transactions,
                                Clock clock) {
        this.sessions = sessions;
        this.configurations = configurations;
        this.hasher = hasher;
        this.locks = locks;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Replaces the whole target schema (spec: target-schema). */
    public ConfigUpdateResult updateSchema(UUID sessionId, List<FieldSpec> fields) {
        return update(sessionId, (session, configuration) -> configuration.withSchema(TargetSchema.define(fields)));
    }

    /** Replaces the whole mapping, checked against the current schema and the file's columns (spec: field-mapping). */
    public ConfigUpdateResult updateMapping(UUID sessionId, List<MappingSpec> mappings) {
        return update(sessionId, (session, configuration) -> configuration.withMapping(MappingConfig.define(
                mappings, configuration.schema(), session.sourceSchema().orElseThrow())));
    }

    /**
     * Applies {@code mutation} to the stored configuration and moves the session to READY or CONFIGURING.
     * The lock wraps the transaction, so the next write on this session sees this one committed. A rejected
     * change rolls back, leaving both the session and its configuration as they were.
     */
    private ConfigUpdateResult update(UUID sessionId,
                                      BiFunction<ImportSession, ImportConfiguration, ConfigChange> mutation) {
        return locks.withLock(sessionId, () -> transactions.execute(status -> {
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
            if (session.status() != SessionStatus.PROCESSED || changed) {
                session.transitionTo(newReadiness.ready() ? SessionStatus.READY : SessionStatus.CONFIGURING, now);
                // F08: when a PROCESSED session changes, its result/ is deleted here.
            }
            ImportConfiguration saved = configurations.save(change.configuration(), now);
            return new ConfigUpdateResult(sessions.save(session), saved, newReadiness, change.warnings());
        }));
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
