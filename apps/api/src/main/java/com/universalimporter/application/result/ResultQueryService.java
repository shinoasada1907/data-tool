package com.universalimporter.application.result;

import com.universalimporter.application.common.SessionLocks;
import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.config.ConfigHasher;
import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.config.ImportConfigurationRepository;
import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.ImportSessionRepository;
import com.universalimporter.domain.importsession.SessionStatus;
import com.universalimporter.domain.pipeline.ImportError;
import com.universalimporter.domain.pipeline.ResultStore;
import com.universalimporter.domain.pipeline.ResultSummary;
import com.universalimporter.domain.pipeline.ResultView;
import com.universalimporter.domain.pipeline.RowResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Reads the stored result of a session a page at a time (design F09-D2, D3, D4).
 * <p>
 * Unlike the plan (D11: reads take no lock), reading holds the session lock. On Windows a directory cannot be
 * renamed while a file inside it is open, so a read overlapping a run's commit, or a configuration change's
 * delete, would make those fail. A read is short, a scan of at most a few megabytes.
 */
@Service
public class ResultQueryService {

    private final ImportSessionRepository sessions;
    private final ImportConfigurationRepository configurations;
    private final ConfigHasher hasher;
    private final ResultStore results;
    private final SessionLocks locks;

    public ResultQueryService(ImportSessionRepository sessions, ImportConfigurationRepository configurations,
                              ConfigHasher hasher, ResultStore results, SessionLocks locks) {
        this.sessions = sessions;
        this.configurations = configurations;
        this.hasher = hasher;
        this.results = results;
        this.locks = locks;
    }

    /**
     * @throws DomainException {@code SESSION_NOT_FOUND}, or {@code RESULT_NOT_AVAILABLE} as for
     *                         {@link #requireCurrentSummary}
     */
    public ResultPage query(UUID sessionId, ResultQuery query) {
        return locks.withLock(sessionId, () -> {
            ResultSummary summary = requireCurrentSummary(sessionId);
            Predicate<RowResult> filter = filter(query);
            long skip = (long) query.page() * query.size();
            List<RowResult> rows;
            long total;
            try (Stream<RowResult> all = results.readRows(sessionId, query.view())) {
                if (filter == null) {
                    // The summary has the count, so reading stops at the end of the page.
                    total = query.view() == ResultView.VALID ? summary.valid() : summary.invalid();
                    rows = all.skip(skip).limit(query.size()).toList();
                } else {
                    PageCollector page = new PageCollector(skip, query.size());
                    all.filter(filter).forEach(page::offer);
                    total = page.matched;
                    rows = page.rows;
                }
            }
            int totalPages = (int) ((total + query.size() - 1) / query.size());
            return new ResultPage(summary, SessionStatus.PROCESSED, query.view(), query.page(), query.size(), total,
                    totalPages, rows);
        });
    }

    /**
     * The summary of a result that may be served (design F09-D4), checked in this order: the session exists
     * ({@code SESSION_NOT_FOUND}); it is PROCESSED, a result is stored, and that result was made with the current
     * configuration (otherwise {@code RESULT_NOT_AVAILABLE}). Export (BE-F10) checks the same way.
     */
    public ResultSummary requireCurrentSummary(UUID sessionId) {
        return locks.withLock(sessionId, () -> {
            ImportSession session = sessions.findById(sessionId)
                    .orElseThrow(() -> new DomainException(ErrorCode.SESSION_NOT_FOUND, "Import session not found."));
            if (session.status() != SessionStatus.PROCESSED) {
                throw notAvailable();
            }
            ResultSummary summary = results.findSummary(sessionId).orElseThrow(ResultQueryService::notAvailable);
            ImportConfiguration configuration = configurations.findBySessionId(sessionId)
                    .orElseGet(() -> ImportConfiguration.empty(sessionId));
            if (!summary.configHash().equals(hasher.hash(configuration))) {
                throw notAvailable();
            }
            return summary;
        });
    }

    /** {@code null} when nothing is filtered: filters apply to invalid rows only (F09-D2). */
    private static Predicate<RowResult> filter(ResultQuery query) {
        if (query.view() != ResultView.INVALID || (query.field() == null && query.code() == null)) {
            return null;
        }
        Predicate<ImportError> matches = error -> (query.field() == null || query.field().equals(error.fieldName()))
                && (query.code() == null || query.code().equals(error.code().name()));
        return row -> row.errors().stream().anyMatch(matches);
    }

    private static DomainException notAvailable() {
        return new DomainException(ErrorCode.RESULT_NOT_AVAILABLE,
                "No result for the current configuration; process the session first.");
    }

    /** Counts every matching row and keeps those of the page. */
    private static final class PageCollector {

        private final long skip;
        private final int size;
        private final List<RowResult> rows = new ArrayList<>();
        private long matched;

        PageCollector(long skip, int size) {
            this.skip = skip;
            this.size = size;
        }

        void offer(RowResult row) {
            if (matched >= skip && rows.size() < size) {
                rows.add(row);
            }
            matched++;
        }
    }
}
