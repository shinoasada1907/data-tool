package com.universaldatatools.tools.importer.application.result;

import com.universaldatatools.tools.importer.application.common.SessionLocks;
import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.RowErrorCode;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.tools.importer.domain.config.ConfigHasher;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;
import com.universaldatatools.tools.importer.domain.importsession.SourceFile;
import com.universaldatatools.tools.importer.domain.pipeline.ErrorStage;
import com.universaldatatools.tools.importer.domain.pipeline.ImportError;
import com.universaldatatools.tools.importer.domain.pipeline.ResultSummary;
import com.universaldatatools.tools.importer.domain.pipeline.ResultView;
import com.universaldatatools.tools.importer.domain.pipeline.RowResult;
import com.universaldatatools.tools.importer.domain.pipeline.SampleDataset;
import com.universaldatatools.support.InMemoryImportConfigurationRepository;
import com.universaldatatools.support.InMemoryImportSessionRepository;
import com.universaldatatools.support.InMemoryResultStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ResultQueryServiceTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final Instant T0 = Instant.parse("2026-09-27T09:00:00Z");
    private static final ResultSummary SUMMARY = summary();

    private final InMemoryImportSessionRepository sessions = new InMemoryImportSessionRepository();
    private final InMemoryImportConfigurationRepository configurations = new InMemoryImportConfigurationRepository();
    private final InMemoryResultStore results = new InMemoryResultStore();
    private final SessionLocks locks = new SessionLocks();

    private ResultQueryService service(String currentHash) {
        ConfigHasher hasher = configuration -> currentHash;
        return new ResultQueryService(sessions, configurations, hasher, results, locks);
    }

    private ResultQueryService service() {
        return service("h1");
    }

    @BeforeEach
    void processedSample() {
        givenSession(SessionStatus.PROCESSED);
        results.put(ID, new InMemoryResultStore.Stored(SUMMARY, List.of(
                new RowResult(2, true, Map.of("name", "An"), List.of()),
                new RowResult(3, false, Map.of("email", "abc"), List.of(
                        error(3, "email", ErrorStage.VALIDATION, "email", null, RowErrorCode.VALIDATION_EMAIL))),
                new RowResult(4, false, Map.of("score", "x"), List.of(
                        error(4, "dob", ErrorStage.TRANSFORMATION, "dateFormat", 0, RowErrorCode.TRANSFORMATION_FAILED),
                        error(4, "score", ErrorStage.VALIDATION, "type", null, RowErrorCode.VALIDATION_TYPE))),
                new RowResult(5, true, Map.of("name", "Em"), List.of()),
                new RowResult(6, false, Map.of("email", "an@x.com"), List.of(
                        error(6, "email", ErrorStage.VALIDATION, "unique", null, RowErrorCode.VALIDATION_UNIQUE))))));
    }

    @Test
    void the_valid_view_pages_from_the_summary_count() {
        ResultPage page = service().query(ID, new ResultQuery(ResultView.VALID, 0, 50, null, null));

        assertThat(rowNumbers(page)).containsExactly(2, 5);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.summary()).isEqualTo(SUMMARY);
        assertThat(page.status()).isEqualTo(SessionStatus.PROCESSED);
        assertThat(page.view()).isEqualTo(ResultView.VALID);
    }

    @Test
    void without_a_filter_the_total_is_the_summary_count_not_a_count_of_rows() {
        ResultSummary claimsSeven = new ResultSummary(10, 7, 3, SUMMARY.errorCountsByCode(),
                SUMMARY.errorCountsByField(), T0, "h1");
        results.put(ID, new InMemoryResultStore.Stored(claimsSeven, results.stored(ID).orElseThrow().rows()));

        ResultPage page = service().query(ID, new ResultQuery(ResultView.VALID, 0, 5, null, null));

        assertThat(page.totalElements()).isEqualTo(7);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void the_first_page_of_invalid_rows() {
        ResultPage page = service().query(ID, new ResultQuery(ResultView.INVALID, 0, 2, null, null));

        assertThat(rowNumbers(page)).containsExactly(3, 4);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(2);
    }

    @Test
    void the_last_page_is_not_full() {
        assertThat(rowNumbers(service().query(ID, new ResultQuery(ResultView.INVALID, 1, 2, null, null))))
                .containsExactly(6);
    }

    @Test
    void a_page_past_the_end_is_empty_but_keeps_the_totals() {
        ResultPage page = service().query(ID, new ResultQuery(ResultView.INVALID, 5, 2, null, null));

        assertThat(page.rows()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void a_page_past_the_end_without_a_filter_opens_nothing() {
        ResultPage page = service().query(ID, new ResultQuery(ResultView.INVALID, 2, 2, null, null));

        assertThat(page.rows()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(results.opened()).isZero();
    }

    @Test
    void the_largest_page_number_is_an_empty_page_not_an_error() {
        ResultPage page = service().query(ID, new ResultQuery(ResultView.INVALID, Integer.MAX_VALUE, 200, "email", null));

        assertThat(page.rows()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.page()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void a_query_out_of_range_is_a_programming_error() {
        assertThat(catchThrowableOfType(IllegalArgumentException.class,
                () -> new ResultQuery(ResultView.VALID, -1, 50, null, null))).isNotNull();
        assertThat(catchThrowableOfType(IllegalArgumentException.class,
                () -> new ResultQuery(ResultView.VALID, 0, 0, null, null))).isNotNull();
        assertThat(catchThrowableOfType(NullPointerException.class,
                () -> new ResultQuery(null, 0, 50, null, null))).isNotNull();
    }

    @Test
    void the_result_is_checked_and_opened_under_the_session_lock() throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            pool.submit(() -> locks.withLock(ID, () -> {
                held.countDown();
                awaitQuietly(release);
                return null;
            }));
            try {
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
                Future<ResultPage> query = pool.submit(() ->
                        service().query(ID, new ResultQuery(ResultView.VALID, 0, 50, null, null)));

                Thread.sleep(200);
                assertThat(query.isDone()).isFalse();
                assertThat(results.opened()).isZero();

                release.countDown();
                assertThat(rowNumbers(query.get(5, TimeUnit.SECONDS))).containsExactly(2, 5);
            } finally {
                // Never leave the lock holder waiting, or closing the pool would hang a failing test.
                release.countDown();
            }
        }
    }

    @Test
    void without_a_filter_reading_stops_at_the_end_of_the_page() {
        ResultPage page = service().query(ID, new ResultQuery(ResultView.INVALID, 0, 1, null, null));

        assertThat(rowNumbers(page)).containsExactly(3);
        assertThat(results.rowsRead()).isEqualTo(1);
    }

    @Test
    void filtering_by_field() {
        ResultPage page = service().query(ID, new ResultQuery(ResultView.INVALID, 0, 50, "email", null));

        assertThat(rowNumbers(page)).containsExactly(3, 6);
        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    void filtering_by_code_keeps_every_error_of_the_row() {
        ResultPage page = service().query(ID, new ResultQuery(ResultView.INVALID, 0, 50, null, "VALIDATION_TYPE"));

        assertThat(rowNumbers(page)).containsExactly(4);
        assertThat(page.rows().getFirst().errors()).hasSize(2);
    }

    @Test
    void both_filters_must_match_the_same_error() {
        ResultPage page = service().query(ID, new ResultQuery(ResultView.INVALID, 0, 50, "email", "VALIDATION_TYPE"));

        assertThat(page.rows()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void field_and_code_of_one_error() {
        assertThat(rowNumbers(service().query(ID,
                new ResultQuery(ResultView.INVALID, 0, 50, "dob", "TRANSFORMATION_FAILED")))).containsExactly(4);
    }

    @Test
    void an_unknown_code_matches_nothing() {
        ResultPage page = service().query(ID, new ResultQuery(ResultView.INVALID, 0, 50, null, "NOPE"));

        assertThat(page.rows()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    void filters_are_ignored_on_the_valid_view() {
        assertThat(rowNumbers(service().query(ID, new ResultQuery(ResultView.VALID, 0, 50, "email", null))))
                .containsExactly(2, 5);
    }

    @Test
    void a_filtered_page_counts_every_match() {
        ResultPage page = service().query(ID, new ResultQuery(ResultView.INVALID, 1, 1, "email", null));

        assertThat(rowNumbers(page)).containsExactly(6);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void a_session_that_is_only_ready_has_no_result() {
        givenSession(SessionStatus.READY);

        assertNotAvailable(service());
    }

    @Test
    void a_failed_session_has_no_result() {
        givenSession(SessionStatus.FAILED);

        assertNotAvailable(service());
    }

    @Test
    void a_processed_session_without_a_stored_result_has_none() {
        results.delete(ID);

        assertNotAvailable(service());
    }

    @Test
    void a_result_of_another_configuration_is_not_served() {
        assertNotAvailable(service("h2"));
    }

    @Test
    void an_unknown_session_is_not_found() {
        DomainException ex = catchThrowableOfType(DomainException.class, () ->
                service().query(UUID.randomUUID(), new ResultQuery(ResultView.VALID, 0, 50, null, null)));

        assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void the_current_result_comes_with_its_configuration_and_open_rows() {
        try (CurrentResult current = service().openCurrent(ID, ResultView.INVALID)) {
            assertThat(current.summary()).isEqualTo(SUMMARY);
            assertThat(current.configuration().sessionId()).isEqualTo(ID);
            assertThat(current.rows().map(RowResult::rowNumber).toList()).containsExactly(3, 4, 6);
        }
    }

    @Test
    void opened_rows_do_not_change_when_the_result_is_deleted_afterwards() {
        try (CurrentResult current = service().openCurrent(ID, ResultView.VALID)) {
            results.delete(ID);

            assertThat(current.rows().map(RowResult::rowNumber).toList()).containsExactly(2, 5);
        }
    }

    private void assertNotAvailable(ResultQueryService service) {
        DomainException ex = catchThrowableOfType(DomainException.class, () ->
                service.query(ID, new ResultQuery(ResultView.VALID, 0, 50, null, null)));

        assertThat(ex.code()).isEqualTo(ErrorCode.RESULT_NOT_AVAILABLE);
        assertThat(catchThrowableOfType(DomainException.class, () -> service.openCurrent(ID, ResultView.VALID)).code())
                .isEqualTo(ErrorCode.RESULT_NOT_AVAILABLE);
        assertThat(results.opened()).isZero();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void givenSession(SessionStatus status) {
        sessions.save(ImportSession.restore(ID, new SourceFile("customers.csv", DataFormat.CSV, 100), status,
                T0, T0, 0L, SampleDataset.SOURCE));
    }

    private static List<Integer> rowNumbers(ResultPage page) {
        return page.rows().stream().map(RowResult::rowNumber).toList();
    }

    private static ImportError error(int row, String field, ErrorStage stage, String rule, Integer step,
                                     RowErrorCode code) {
        return new ImportError(row, field, stage, rule, step, code, "Message.", "v");
    }

    private static ResultSummary summary() {
        Map<String, Long> byCode = new LinkedHashMap<>();
        byCode.put("TRANSFORMATION_FAILED", 1L);
        byCode.put("VALIDATION_EMAIL", 1L);
        byCode.put("VALIDATION_TYPE", 1L);
        byCode.put("VALIDATION_UNIQUE", 1L);
        Map<String, Long> byField = new LinkedHashMap<>();
        byField.put("email", 2L);
        byField.put("dob", 1L);
        byField.put("score", 1L);
        return new ResultSummary(5, 2, 3, byCode, byField, T0, "h1");
    }
}
