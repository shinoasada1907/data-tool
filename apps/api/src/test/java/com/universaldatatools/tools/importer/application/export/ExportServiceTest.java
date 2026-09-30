package com.universaldatatools.tools.importer.application.export;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.RowErrorCode;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.support.CsvTestReader;
import com.universaldatatools.support.InMemoryImportConfigurationRepository;
import com.universaldatatools.support.InMemoryImportSessionRepository;
import com.universaldatatools.support.InMemoryResultStore;
import com.universaldatatools.tools.importer.application.common.SessionLocks;
import com.universaldatatools.tools.importer.application.result.ResultQueryService;
import com.universaldatatools.tools.importer.domain.config.ConfigHasher;
import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.export.ExportFormat;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;
import com.universaldatatools.tools.importer.domain.importsession.SourceFile;
import com.universaldatatools.tools.importer.domain.pipeline.ErrorStage;
import com.universaldatatools.tools.importer.domain.pipeline.ImportError;
import com.universaldatatools.tools.importer.domain.pipeline.ResultSummary;
import com.universaldatatools.tools.importer.domain.pipeline.RowResult;
import com.universaldatatools.tools.importer.domain.pipeline.SampleDataset;
import com.universaldatatools.tools.importer.domain.schema.FieldSpec;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;
import com.universaldatatools.tools.importer.infrastructure.export.CsvErrorReportExporter;
import com.universaldatatools.tools.importer.infrastructure.export.CsvValidRowsExporter;
import com.universaldatatools.tools.importer.infrastructure.export.JsonValidRowsExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ExportServiceTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final Instant T0 = Instant.parse("2026-09-27T09:00:00Z");
    private static final ConfigHasher HASHER = configuration -> "h1";

    private final InMemoryImportSessionRepository sessions = new InMemoryImportSessionRepository();
    private final InMemoryImportConfigurationRepository configurations = new InMemoryImportConfigurationRepository();
    private final InMemoryResultStore results = new InMemoryResultStore();
    private final SessionLocks locks = new SessionLocks();
    private final ExportService service = new ExportService(sessions,
            new ResultQueryService(sessions, configurations, HASHER, results, locks),
            List.of(new JsonValidRowsExporter(),
                    new CsvValidRowsExporter()),
            new CsvErrorReportExporter());

    @BeforeEach
    void processedWithOneValidAndOneInvalidRow() {
        givenSession(SessionStatus.PROCESSED);
        configurations.save(ImportConfiguration.empty(ID).withSchema(TargetSchema.define(List.of(
                new FieldSpec("name", "string", true, 0), new FieldSpec("email", "email", true, 1)))).configuration(), T0);
        results.put(ID, new InMemoryResultStore.Stored(new ResultSummary(2, 1, 1, Map.of(), Map.of(), T0, "h1"), List.of(
                new RowResult(2, true, Map.of("name", "An", "email", "an@x.com"), List.of()),
                new RowResult(3, false, Map.of("name", "Binh", "email", "not-an-email"), List.of(
                        new ImportError(3, "email", ErrorStage.VALIDATION, "email", null, RowErrorCode.VALIDATION_EMAIL,
                                "Value is not a valid email address.", "not-an-email"))))));
    }

    @Test
    void valid_rows_as_json() throws IOException {
        try (ExportDownload download = service.prepareValidRows(ID, ExportFormat.JSON)) {
            assertThat(download.fileName()).isEqualTo("customers-valid.json");
            assertThat(download.contentType()).isEqualTo("application/json");
            assertThat(body(download)).isEqualTo("[{\"name\":\"An\",\"email\":\"an@x.com\"}]");
        }
        assertThat(results.closedStreams()).isEqualTo(1);
    }

    @Test
    void a_download_that_is_never_written_still_releases_its_rows() {
        ExportDownload download = service.prepareErrorReport(ID);

        download.close();
        download.close();

        assertThat(results.opened()).isEqualTo(1);
        assertThat(results.closedStreams()).isEqualTo(1);
    }

    @Test
    void a_failure_to_release_the_rows_after_writing_them_all_is_not_an_error() throws IOException {
        results.failCloses();
        ExportDownload download = service.prepareValidRows(ID, ExportFormat.JSON);

        assertThat(body(download)).isEqualTo("[{\"name\":\"An\",\"email\":\"an@x.com\"}]");
        download.close();

        assertThat(results.closedStreams()).isEqualTo(1);
    }

    @Test
    void valid_rows_as_csv() throws IOException {
        try (ExportDownload download = service.prepareValidRows(ID, ExportFormat.CSV)) {
            assertThat(download.fileName()).isEqualTo("customers-valid.csv");
            assertThat(download.contentType()).isEqualTo("text/csv;charset=UTF-8");
            assertThat(CsvTestReader.read(bytes(download)))
                    .containsExactly(List.of("name", "email"), List.of("An", "an@x.com"));
        }
    }

    @Test
    void the_error_report_reads_the_invalid_rows() throws IOException {
        try (ExportDownload download = service.prepareErrorReport(ID)) {
            assertThat(download.fileName()).isEqualTo("customers-errors.csv");
            assertThat(download.contentType()).isEqualTo("text/csv;charset=UTF-8");
            assertThat(CsvTestReader.read(bytes(download))).hasSize(2).last()
                    .isEqualTo(List.of("3", "email", "VALIDATION", "email", "", "VALIDATION_EMAIL",
                            "Value is not a valid email address.", "not-an-email"));
        }
    }

    @Test
    void no_current_result_is_refused_before_anything_is_opened() {
        givenSession(SessionStatus.READY);

        assertThat(catchThrowableOfType(DomainException.class, () -> service.prepareValidRows(ID, ExportFormat.JSON)).code())
                .isEqualTo(ErrorCode.RESULT_NOT_AVAILABLE);
        assertThat(catchThrowableOfType(DomainException.class, () -> service.prepareErrorReport(ID)).code())
                .isEqualTo(ErrorCode.RESULT_NOT_AVAILABLE);
        assertThat(results.opened()).isZero();
    }

    @Test
    void an_unknown_session_is_not_found() {
        assertThat(catchThrowableOfType(DomainException.class,
                () -> service.prepareValidRows(UUID.randomUUID(), ExportFormat.CSV)).code())
                .isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void rows_that_cannot_be_opened_are_an_export_failure() {
        results.failReads();

        assertThat(catchThrowableOfType(DomainException.class, () -> service.prepareValidRows(ID, ExportFormat.JSON)).code())
                .isEqualTo(ErrorCode.EXPORT_FAILED);
    }

    @Test
    void the_file_is_the_result_that_was_checked_even_if_it_is_deleted_before_streaming() throws IOException {
        try (ExportDownload download = service.prepareValidRows(ID, ExportFormat.JSON)) {
            results.delete(ID);

            assertThat(body(download)).contains("an@x.com");
        }
    }

    @Test
    void streaming_does_not_hold_the_session_lock() throws Exception {
        boolean[] lockFree = {false};
        try (ExportDownload download = service.prepareValidRows(ID, ExportFormat.JSON);
             ExecutorService other = Executors.newSingleThreadExecutor()) {
            download.body().writeTo(new ByteArrayOutputStream() {
                @Override
                public void write(byte[] bytes, int offset, int length) {
                    super.write(bytes, offset, length);
                    try {
                        lockFree[0] = other.submit(() -> locks.withLock(ID, () -> true)).get(2, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        lockFree[0] = false;
                    }
                }
            });
        }

        assertThat(lockFree[0]).isTrue();
    }

    private void givenSession(SessionStatus status) {
        sessions.save(ImportSession.restore(ID, new SourceFile("customers.csv", DataFormat.CSV, 100), status,
                T0, T0, 0L, SampleDataset.SOURCE));
    }

    private static byte[] bytes(ExportDownload download) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        download.body().writeTo(out);
        return out.toByteArray();
    }

    private static String body(ExportDownload download) throws IOException {
        return new String(bytes(download), StandardCharsets.UTF_8);
    }
}
