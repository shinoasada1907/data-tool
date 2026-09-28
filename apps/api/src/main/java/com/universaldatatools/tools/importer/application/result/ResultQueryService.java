package com.universaldatatools.tools.importer.application.result;

import com.universaldatatools.tools.importer.application.common.SessionLocks;
import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.tools.importer.domain.config.ConfigHasher;
import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.config.ImportConfigurationRepository;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.ImportSessionRepository;
import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;
import com.universaldatatools.tools.importer.domain.pipeline.ImportError;
import com.universaldatatools.tools.importer.domain.pipeline.ResultStore;
import com.universaldatatools.tools.importer.domain.pipeline.ResultSummary;
import com.universaldatatools.tools.importer.domain.pipeline.ResultView;
import com.universaldatatools.tools.importer.domain.pipeline.RowResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Reads the stored result of a session (design F09-D2, D3, D4).
 * <p>
 * The check that a result may be served and the opening of its rows happen together under the session lock, so
 * the rows always belong to the summary that was checked. The rows are read after the lock is released: the store
 * hands out detached streams, which neither see nor block a run or a configuration change that comes next.
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
     * @throws DomainException {@code SESSION_NOT_FOUND}, or {@code RESULT_NOT_AVAILABLE} as for {@link #openCurrent}
     */
    public ResultPage query(UUID sessionId, ResultQuery query) {
        Predicate<RowResult> filter = filter(query);
        long skip = (long) query.page() * query.size();
        Opened opened = locks.withLock(sessionId, () -> {
            Checked checked = checkCurrent(sessionId);
            long viewTotal = query.view() == ResultView.VALID ? checked.summary().valid() : checked.summary().invalid();
            if (filter == null && skip >= viewTotal) {
                return new Opened(checked.summary(), Stream.empty());
            }
            // Without a filter the unwanted rows are skipped unparsed; with one, every row must be looked at.
            return new Opened(checked.summary(), results.readRows(sessionId, query.view(), filter == null ? skip : 0));
        });
        List<RowResult> rows;
        long total;
        try (Stream<RowResult> all = opened.rows()) {
            if (filter == null) {
                // The summary has the count, so reading stops at the end of the page.
                total = query.view() == ResultView.VALID ? opened.summary().valid() : opened.summary().invalid();
                rows = all.limit(query.size()).toList();
            } else {
                PageCollector page = new PageCollector(skip, query.size());
                all.filter(filter).forEach(page::offer);
                total = page.matched;
                rows = page.rows;
            }
        }
        int totalPages = (int) ((total + query.size() - 1) / query.size());
        return new ResultPage(opened.summary(), SessionStatus.PROCESSED, query.view(), query.page(), query.size(),
                total, totalPages, rows);
    }

    /**
     * Checks that a result may be served (design F09-D4) and opens its rows of {@code view}, in one step under
     * the session lock. Checked in this order: the session exists ({@code SESSION_NOT_FOUND}); it is PROCESSED, a
     * result is stored, and that result was made with the current configuration (otherwise
     * {@code RESULT_NOT_AVAILABLE}). Export (BE-F10) uses it, so it answers exactly as {@code GET /result}.
     */
    public CurrentResult openCurrent(UUID sessionId, ResultView view) {
        return locks.withLock(sessionId, () -> {
            Checked checked = checkCurrent(sessionId);
            return new CurrentResult(checked.summary(), checked.configuration(), results.readRows(sessionId, view, 0));
        });
    }

    /** Call it holding the session lock. */
    private Checked checkCurrent(UUID sessionId) {
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
        return new Checked(summary, configuration);
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

    private record Checked(ResultSummary summary, ImportConfiguration configuration) {
    }

    private record Opened(ResultSummary summary, Stream<RowResult> rows) {
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
