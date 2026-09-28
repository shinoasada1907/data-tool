package com.universaldatatools.tools.importer.application.pipeline;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.table.SourceParser;
import com.universaldatatools.platform.storage.FileStorage;
import com.universaldatatools.tools.importer.application.common.SessionLocks;
import com.universaldatatools.tools.importer.application.importsession.SourceParsers;
import com.universaldatatools.tools.importer.domain.config.ConfigHasher;
import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.config.ImportConfigurationRepository;
import com.universaldatatools.tools.importer.domain.config.Readiness;
import com.universaldatatools.tools.importer.domain.config.ReadinessEvaluator;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.ImportSessionRepository;
import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;
import com.universaldatatools.tools.importer.domain.pipeline.ImportPipeline;
import com.universaldatatools.tools.importer.domain.pipeline.PipelineConfig;
import com.universaldatatools.tools.importer.domain.pipeline.PipelineSummary;
import com.universaldatatools.tools.importer.domain.pipeline.ResultStore;
import com.universaldatatools.tools.importer.domain.pipeline.ResultSummary;
import com.universaldatatools.tools.importer.domain.pipeline.ResultWriter;
import com.universaldatatools.tools.importer.domain.pipeline.RowResultSink;
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
     *                         {@code SESSION_NOT_READY} (with the readiness issues), the parser's own error (such as
     *                         {@code FILE_PARSE_ERROR}) or {@code INTERNAL_ERROR}. A source that cannot be read to
     *                         the end fails the session for good (design D2); a result that cannot be stored leaves
     *                         the session and its previous result as they were.
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
        if (session.status() == SessionStatus.CONFIGURING) {
            // Only when statuses predate a readiness rule. Both PROCESSED and FAILED are reached through READY.
            session.transitionTo(SessionStatus.READY, now);
        }

        ResultSummary summary;
        try {
            summary = execute(sessionId, parser, pipelineConfig, now, configHash);
        } catch (SourceFailure e) {
            fail(session, now);
            throw e.reported();
        } catch (ResultWriteFailure e) {
            // The store, not the file, failed (a full disk, say): the session and any earlier result stay as they were.
            log.error("Result of session {} could not be stored", sessionId, e.getCause());
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "The result could not be stored.");
        }
        session.transitionTo(SessionStatus.PROCESSED, now);
        ImportSession saved = sessions.save(session);
        log.info("Processed import session {}: {} rows, {} valid, {} invalid", sessionId, summary.total(),
                summary.valid(), summary.invalid());
        return new PipelineSummaryView(sessionId, saved.status(), summary);
    }

    /**
     * Three phases, so a failure is blamed on the right party: the result is begun, the whole source is read into
     * it, and only once the source is closed is the result committed.
     *
     * @throws SourceFailure      the source could not be read to the end
     * @throws ResultWriteFailure the result could not be written or put in place
     */
    private ResultSummary execute(UUID sessionId, SourceParser parser, PipelineConfig config, Instant now,
                                  String configHash) {
        try (ResultWriter writer = beginResult(sessionId)) {
            PipelineSummary summary = readThrough(sessionId, parser, config, row -> {
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
        }
    }

    /**
     * Runs the pipeline over every row of the source. Once the last row is read the run is complete, so a failure
     * to close the file afterwards (a temporary copy still locked, say) is only logged.
     */
    private PipelineSummary readThrough(UUID sessionId, SourceParser parser, PipelineConfig config, RowResultSink sink) {
        PipelineSummary summary = null;
        try (InputStream in = storage.open(sessionId); Stream<Row> rows = parser.read(in)) {
            summary = pipeline.execute(rows, config, sink);
        } catch (ResultWriteFailure e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            if (summary != null) {
                log.warn("Source file of session {} was read but could not be closed: {}", sessionId,
                        e.getClass().getName());
            } else if (e instanceof DomainException || e instanceof IOException || e instanceof UncheckedIOException) {
                throw new SourceFailure(sessionId, e);
            } else {
                // A bug, not the file: the session stays as it was.
                throw (RuntimeException) e;
            }
        }
        return summary;
    }

    private ResultWriter beginResult(UUID sessionId) {
        try {
            return results.begin(sessionId);
        } catch (UncheckedIOException e) {
            throw new ResultWriteFailure(e);
        }
    }

    /**
     * FAILED is final (design D2), and saved first. Its old result goes too; that deletion is best effort, since
     * a failed session serves no result anyway (BE-F09 checks the status) and must not hide the original error.
     */
    private void fail(ImportSession session, Instant now) {
        session.transitionTo(SessionStatus.FAILED, now);
        sessions.save(session);
        try {
            results.delete(session.id());
        } catch (RuntimeException e) {
            log.warn("Result of failed session {} could not be deleted: {}", session.id(), e.getClass().getName());
        }
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

    /** Marks a failure to read the source to the end, whatever its kind. */
    private static final class SourceFailure extends RuntimeException {

        private final UUID sessionId;

        SourceFailure(UUID sessionId, Exception cause) {
            super(cause);
            this.sessionId = sessionId;
        }

        /** What the client is told: the parser's own error as it is (FILE_PARSE_ERROR, FILE_EMPTY...), I/O as internal. */
        DomainException reported() {
            return switch (getCause()) {
                case DomainException e -> e;
                case UncheckedIOException e -> unreadable(e.getCause());
                case IOException e -> unreadable(e);
                default -> throw new IllegalStateException("Not a source failure", getCause());
            };
        }

        private DomainException unreadable(IOException e) {
            log.warn("Source file of session {} could not be read: {}", sessionId, e.getClass().getName());
            return new DomainException(ErrorCode.INTERNAL_ERROR, "Source file could not be read.");
        }
    }
}
