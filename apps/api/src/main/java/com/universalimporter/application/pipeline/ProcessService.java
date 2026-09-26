package com.universalimporter.application.pipeline;

import com.universalimporter.application.common.SessionLocks;
import com.universalimporter.application.importsession.SourceParsers;
import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.config.ConfigHasher;
import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.config.ImportConfigurationRepository;
import com.universalimporter.domain.config.Readiness;
import com.universalimporter.domain.config.ReadinessEvaluator;
import com.universalimporter.domain.importsession.FileStorage;
import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.ImportSessionRepository;
import com.universalimporter.domain.importsession.SessionStatus;
import com.universalimporter.domain.pipeline.ImportPipeline;
import com.universalimporter.domain.pipeline.PipelineConfig;
import com.universalimporter.domain.pipeline.PipelineSummary;
import com.universalimporter.domain.pipeline.ResultStore;
import com.universalimporter.domain.pipeline.ResultSummary;
import com.universalimporter.domain.pipeline.ResultWriter;
import com.universalimporter.domain.source.ImportRow;
import com.universalimporter.domain.source.SourceParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Runs the pipeline over a session's whole file and stores the result (design P6). Synchronous, and inside the
 * session lock (D11), so it never overlaps a configuration change or another run of the same session. Not one
 * transaction: a run can take seconds, and the session is saved once, at the end.
 */
@Service
public class ProcessService {

    private static final Logger log = LoggerFactory.getLogger(ProcessService.class);

    private final ImportSessionRepository sessions;
    private final ImportConfigurationRepository configurations;
    private final FileStorage storage;
    private final SourceParsers parsers;
    private final ImportPipeline pipeline;
    private final ResultStore results;
    private final ConfigHasher hasher;
    private final SessionLocks locks;
    private final Clock clock;

    public ProcessService(ImportSessionRepository sessions, ImportConfigurationRepository configurations,
                          FileStorage storage, SourceParsers parsers, ImportPipeline pipeline, ResultStore results,
                          ConfigHasher hasher, SessionLocks locks, Clock clock) {
        this.sessions = sessions;
        this.configurations = configurations;
        this.storage = storage;
        this.parsers = parsers;
        this.pipeline = pipeline;
        this.results = results;
        this.hasher = hasher;
        this.locks = locks;
        this.clock = clock;
    }

    /**
     * @throws DomainException {@code SESSION_NOT_FOUND}, {@code SESSION_STATE_INVALID} (failed session),
     *                         {@code SESSION_NOT_READY} (with the readiness issues), {@code FILE_PARSE_ERROR} or
     *                         {@code INTERNAL_ERROR}; the last two when reading the source fails, which fails the
     *                         session for good (design D2)
     */
    public PipelineSummaryView process(UUID sessionId) {
        return locks.withLock(sessionId, () -> run(sessionId));
    }

    private PipelineSummaryView run(UUID sessionId) {
        ImportSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.SESSION_NOT_FOUND, "Import session not found."));
        if (session.status() == SessionStatus.FAILED) {
            throw new DomainException(ErrorCode.SESSION_STATE_INVALID, "Session has failed and cannot be changed.");
        }
        ImportConfiguration configuration = configurations.findBySessionId(sessionId)
                .orElseGet(() -> ImportConfiguration.empty(sessionId));
        Readiness readiness = ReadinessEvaluator.standard().evaluate(configuration);
        if (!readiness.ready()) {
            throw new DomainException(ErrorCode.SESSION_NOT_READY, "Session is not ready to process.",
                    readiness.issues());
        }
        // Ready implies configured, and configuring needs the file to have been read at upload.
        PipelineConfig pipelineConfig = new PipelineConfig(
                session.sourceSchema().orElseThrow(() -> new IllegalStateException("Ready session without source schema")),
                configuration.schema(), configuration.mapping(), configuration.transformations(),
                configuration.validations());
        SourceParser parser = parsers.find(session.sourceFile().fileType())
                .orElseThrow(() -> new IllegalStateException("No parser for " + session.sourceFile().fileType()));
        String configHash = hasher.hash(configuration);
        Instant now = now();

        ResultSummary summary;
        try {
            summary = execute(sessionId, parser, pipelineConfig, now, configHash);
        } catch (DomainException e) {
            if (e.code() == ErrorCode.FILE_PARSE_ERROR) {
                fail(session, now);
            }
            throw e;
        } catch (UncheckedIOException e) {
            log.warn("Source file of session {} could not be read: {}", sessionId, e.getCause().getClass().getName());
            fail(session, now);
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "Source file could not be read.");
        } catch (ResultWriteFailure e) {
            // The store, not the file, failed (a full disk, say): the session and any earlier result stay as they were.
            log.error("Result of session {} could not be stored", sessionId, e.getCause());
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "The result could not be stored.");
        }
        if (session.status() == SessionStatus.CONFIGURING) {
            // Only when statuses predate a readiness rule; PROCESSED is reached through READY.
            session.transitionTo(SessionStatus.READY, now);
        }
        session.transitionTo(SessionStatus.PROCESSED, now);
        ImportSession saved = sessions.save(session);
        log.info("Processed import session {}: {} rows, {} valid, {} invalid", sessionId, summary.total(),
                summary.valid(), summary.invalid());
        return new PipelineSummaryView(sessionId, saved.status(), summary);
    }

    /** Reading the source may throw FILE_PARSE_ERROR or UncheckedIOException; storing wraps its own failures. */
    private ResultSummary execute(UUID sessionId, SourceParser parser, PipelineConfig config, Instant now,
                                  String configHash) {
        try (InputStream in = storage.open(sessionId);
             Stream<ImportRow> rows = parser.read(in);
             ResultWriter writer = beginResult(sessionId)) {
            PipelineSummary summary = pipeline.execute(rows, config, row -> {
                try {
                    writer.accept(row);
                } catch (UncheckedIOException e) {
                    throw new ResultWriteFailure(e);
                }
            });
            ResultSummary result = ResultSummary.of(summary, now, configHash);
            try {
                writer.commit(result);
            } catch (UncheckedIOException e) {
                throw new ResultWriteFailure(e);
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot close the source file", e);
        }
    }

    private ResultWriter beginResult(UUID sessionId) {
        try {
            return results.begin(sessionId);
        } catch (UncheckedIOException e) {
            throw new ResultWriteFailure(e);
        }
    }

    /** FAILED is final (design D2): its old result goes too, so nothing stale is ever served. */
    private void fail(ImportSession session, Instant now) {
        results.delete(session.id());
        session.transitionTo(SessionStatus.FAILED, now);
        sessions.save(session);
    }

    /** PostgreSQL keeps microseconds; truncating here keeps memory and database in agreement. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** Marks an I/O failure of the result store, to tell it apart from one of the source file. */
    private static final class ResultWriteFailure extends RuntimeException {

        ResultWriteFailure(UncheckedIOException cause) {
            super(cause);
        }
    }
}
