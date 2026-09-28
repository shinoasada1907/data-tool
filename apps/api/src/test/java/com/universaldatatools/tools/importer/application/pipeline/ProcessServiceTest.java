package com.universaldatatools.tools.importer.application.pipeline;

import com.universaldatatools.core.table.SourceFileType;
import com.universaldatatools.tools.importer.domain.validation.FieldValidator;
import com.universaldatatools.tools.importer.application.common.SessionLocks;
import com.universaldatatools.tools.importer.application.importsession.SourceParsers;
import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.tools.importer.domain.config.ConfigHasher;
import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;
import com.universaldatatools.tools.importer.domain.importsession.SourceFile;
import com.universaldatatools.tools.importer.domain.mapping.MappingConfig;
import com.universaldatatools.tools.importer.domain.mapping.MappingStrategies;
import com.universaldatatools.tools.importer.domain.pipeline.DefaultImportPipeline;
import com.universaldatatools.tools.importer.domain.pipeline.SampleDataset;
import com.universaldatatools.core.transform.TransformationEngine;
import com.universaldatatools.core.transform.TransformationRegistry;
import com.universaldatatools.core.validate.ValidationRegistry;
import com.universaldatatools.support.FakeSourceParser;
import com.universaldatatools.support.InMemoryFileStorage;
import com.universaldatatools.support.InMemoryImportConfigurationRepository;
import com.universaldatatools.support.InMemoryImportSessionRepository;
import com.universaldatatools.support.InMemoryResultStore;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ProcessServiceTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final Instant T0 = Instant.parse("2026-09-25T10:00:00Z");
    private static final ConfigHasher HASHER = configuration -> "hash:" + configuration.schema().fields().size();

    private final InMemoryImportSessionRepository sessions = new InMemoryImportSessionRepository();
    private final InMemoryImportConfigurationRepository configurations = new InMemoryImportConfigurationRepository();
    private final InMemoryFileStorage storage = new InMemoryFileStorage();
    private final InMemoryResultStore results = new InMemoryResultStore();
    private FakeSourceParser parser = FakeSourceParser.forType(SourceFileType.CSV).withRows(SampleDataset.rows());

    private ProcessService service() {
        return new ProcessService(sessions, configurations, storage, new SourceParsers(List.of(parser)),
                new DefaultImportPipeline(MappingStrategies.standard(),
                        new TransformationEngine(TransformationRegistry.standard()),
                        new FieldValidator(ValidationRegistry.standard())),
                results, HASHER, new SessionLocks(), Clock.fixed(T0, ZoneOffset.UTC));
    }

    @Test
    void a_ready_session_is_processed_and_its_result_stored() {
        givenSample(SessionStatus.READY);

        PipelineSummaryView view = service().process(ID);

        assertThat(view.sessionId()).isEqualTo(ID);
        assertThat(view.status()).isEqualTo(SessionStatus.PROCESSED);
        assertThat(view.summary().total()).isEqualTo(6);
        assertThat(view.summary().valid()).isEqualTo(3);
        assertThat(view.summary().invalid()).isEqualTo(3);
        assertThat(sessions.findById(ID).orElseThrow().status()).isEqualTo(SessionStatus.PROCESSED);
        InMemoryResultStore.Stored stored = results.stored(ID).orElseThrow();
        assertThat(stored.summary().configHash()).isEqualTo("hash:4");
        assertThat(stored.summary().processedAt()).isEqualTo(T0);
        assertThat(stored.rows()).hasSize(6);
        assertThat(results.open()).isZero();
    }

    @Test
    void a_session_that_is_not_ready_is_refused_with_its_issues() {
        givenSession(SessionStatus.CONFIGURING);
        configurations.save(ImportConfiguration.empty(ID).withSchema(SampleDataset.SCHEMA).configuration()
                .withMapping(MappingConfig.empty()).configuration(), T0);

        DomainException ex = catchThrowableOfType(DomainException.class, () -> service().process(ID));

        assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_NOT_READY);
        assertThat(ex.items()).extracting(ProblemItem::field, ProblemItem::code).containsExactly(
                org.assertj.core.groups.Tuple.tuple("name", "TARGET_FIELD_REQUIRED"),
                org.assertj.core.groups.Tuple.tuple("email", "TARGET_FIELD_REQUIRED"));
        assertThat(results.begun()).isZero();
    }

    @Test
    void a_failed_session_is_refused() {
        givenSample(SessionStatus.FAILED);

        assertThat(catchThrowableOfType(DomainException.class, () -> service().process(ID)).code())
                .isEqualTo(ErrorCode.SESSION_STATE_INVALID);
    }

    @Test
    void an_unknown_session_is_not_found() {
        assertThat(catchThrowableOfType(DomainException.class, () -> service().process(ID)).code())
                .isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void a_parse_error_fails_the_session_and_removes_every_result() {
        givenSample(SessionStatus.PROCESSED);
        results.put(ID, new InMemoryResultStore.Stored(null, List.of()));
        DomainException parseError = new DomainException(ErrorCode.FILE_PARSE_ERROR, "CSV syntax error near row 4.");
        parser = FakeSourceParser.forType(SourceFileType.CSV).withRows(SampleDataset.rows()).failingAtRow(4, parseError);

        DomainException ex = catchThrowableOfType(DomainException.class, () -> service().process(ID));

        assertThat(ex).isSameAs(parseError);
        assertThat(sessions.findById(ID).orElseThrow().status()).isEqualTo(SessionStatus.FAILED);
        assertThat(results.stored(ID)).isEmpty();
        assertThat(results.open()).isZero();
        assertThat(catchThrowableOfType(DomainException.class, () -> service().process(ID)).code())
                .isEqualTo(ErrorCode.SESSION_STATE_INVALID);
    }

    @Test
    void a_missing_source_file_is_an_internal_error_and_fails_the_session() {
        givenSample(SessionStatus.READY);
        storage.delete(ID);

        DomainException ex = catchThrowableOfType(DomainException.class, () -> service().process(ID));

        assertThat(ex.code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(ex.getMessage()).isEqualTo("Source file could not be read.");
        assertThat(sessions.findById(ID).orElseThrow().status()).isEqualTo(SessionStatus.FAILED);
    }

    @Test
    void a_read_error_mid_file_is_an_internal_error_and_leaves_no_temporary_result() {
        givenSample(SessionStatus.READY);
        parser = FakeSourceParser.forType(SourceFileType.CSV).withRows(SampleDataset.rows())
                .failingAtRow(3, new UncheckedIOException(new IOException("disk gone")));

        DomainException ex = catchThrowableOfType(DomainException.class, () -> service().process(ID));

        assertThat(ex.code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(sessions.findById(ID).orElseThrow().status()).isEqualTo(SessionStatus.FAILED);
        assertThat(results.open()).isZero();
    }

    @Test
    void a_failure_to_write_the_result_is_an_internal_error_that_does_not_fail_the_session() {
        givenSample(SessionStatus.READY);
        results.failWrites();

        DomainException ex = catchThrowableOfType(DomainException.class, () -> service().process(ID));

        assertThat(ex.code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(ex.getMessage()).isEqualTo("The result could not be stored.");
        assertThat(sessions.findById(ID).orElseThrow().status()).isEqualTo(SessionStatus.READY);
        assertThat(results.open()).isZero();
    }

    @Test
    void a_processed_session_can_be_processed_again() {
        givenSample(SessionStatus.READY);
        service().process(ID);

        PipelineSummaryView again = service().process(ID);

        assertThat(again.status()).isEqualTo(SessionStatus.PROCESSED);
        assertThat(results.begun()).isEqualTo(2);
        assertThat(results.stored(ID).orElseThrow().rows()).hasSize(6);
    }

    @Test
    void a_failure_to_close_the_source_after_reading_it_all_does_not_undo_the_run() {
        givenSample(SessionStatus.READY);
        parser = FakeSourceParser.forType(SourceFileType.CSV).withRows(SampleDataset.rows())
                .failingOnClose(new UncheckedIOException(new IOException("temp file still locked")));

        PipelineSummaryView view = service().process(ID);

        assertThat(view.status()).isEqualTo(SessionStatus.PROCESSED);
        assertThat(sessions.findById(ID).orElseThrow().status()).isEqualTo(SessionStatus.PROCESSED);
        assertThat(results.stored(ID)).isPresent();
    }

    @Test
    void any_source_error_fails_the_session_not_only_parse_errors() {
        givenSample(SessionStatus.READY);
        DomainException empty = new DomainException(ErrorCode.FILE_EMPTY, "The file has no header row.");
        parser = FakeSourceParser.forType(SourceFileType.CSV).withRows(SampleDataset.rows()).failingAtRow(2, empty);

        assertThat(catchThrowableOfType(DomainException.class, () -> service().process(ID))).isSameAs(empty);
        assertThat(sessions.findById(ID).orElseThrow().status()).isEqualTo(SessionStatus.FAILED);
    }

    @Test
    void a_failure_to_store_keeps_the_previous_result() {
        givenSample(SessionStatus.PROCESSED);
        InMemoryResultStore.Stored previous = new InMemoryResultStore.Stored(null, List.of());
        results.put(ID, previous);
        results.failWrites();

        catchThrowableOfType(DomainException.class, () -> service().process(ID));

        assertThat(results.stored(ID)).containsSame(previous);
        assertThat(sessions.findById(ID).orElseThrow().status()).isEqualTo(SessionStatus.PROCESSED);
    }

    @Test
    void a_configuring_session_that_is_ready_is_processed_and_can_still_fail() {
        givenSample(SessionStatus.CONFIGURING);

        assertThat(service().process(ID).status()).isEqualTo(SessionStatus.PROCESSED);

        givenSample(SessionStatus.CONFIGURING);
        parser = FakeSourceParser.forType(SourceFileType.CSV).withRows(SampleDataset.rows())
                .failingAtRow(3, new DomainException(ErrorCode.FILE_PARSE_ERROR, "CSV syntax error near row 3."));
        DomainException ex = catchThrowableOfType(DomainException.class, () -> service().process(ID));

        assertThat(ex.code()).isEqualTo(ErrorCode.FILE_PARSE_ERROR);
        assertThat(sessions.findById(ID).orElseThrow().status()).isEqualTo(SessionStatus.FAILED);
    }

    @Test
    void runs_on_one_session_never_overlap() throws Exception {
        givenSample(SessionStatus.READY);
        results.slowCommits(200);
        ProcessService service = service();
        try (java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Future<?> first = pool.submit(() -> service.process(ID));
            java.util.concurrent.Future<?> second = pool.submit(() -> service.process(ID));
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            second.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }

        assertThat(results.begun()).isEqualTo(2);
        assertThat(results.maxConcurrentWriters()).isEqualTo(1);
    }

    @Test
    void a_bug_in_the_pipeline_is_not_blamed_on_the_file() {
        givenSample(SessionStatus.READY);
        IllegalStateException bug = new IllegalStateException("bug");
        ProcessService service = new ProcessService(sessions, configurations, storage, new SourceParsers(List.of(parser)),
                (rows, config, sink) -> {
                    throw bug;
                }, results, HASHER, new SessionLocks(), Clock.fixed(T0, ZoneOffset.UTC));

        assertThat(catchThrowableOfType(IllegalStateException.class, () -> service.process(ID))).isSameAs(bug);
        assertThat(sessions.findById(ID).orElseThrow().status()).isEqualTo(SessionStatus.READY);
        assertThat(results.open()).isZero();
    }

    @Test
    void a_result_that_cannot_be_deleted_does_not_hide_the_parse_error() {
        givenSample(SessionStatus.READY);
        DomainException parseError = new DomainException(ErrorCode.FILE_PARSE_ERROR, "CSV syntax error near row 2.");
        parser = FakeSourceParser.forType(SourceFileType.CSV).withRows(SampleDataset.rows()).failingAtRow(2, parseError);
        InMemoryResultStore stuck = new InMemoryResultStore() {
            @Override
            public void delete(UUID sessionId) {
                throw new UncheckedIOException(new IOException("file in use"));
            }
        };
        ProcessService service = new ProcessService(sessions, configurations, storage, new SourceParsers(List.of(parser)),
                new DefaultImportPipeline(MappingStrategies.standard(),
                        new TransformationEngine(TransformationRegistry.standard()),
                        new FieldValidator(ValidationRegistry.standard())),
                stuck, HASHER, new SessionLocks(), Clock.fixed(T0, ZoneOffset.UTC));

        assertThat(catchThrowableOfType(DomainException.class, () -> service.process(ID))).isSameAs(parseError);
        assertThat(sessions.findById(ID).orElseThrow().status()).isEqualTo(SessionStatus.FAILED);
    }

    private void givenSample(SessionStatus status) {
        givenSession(status);
        configurations.save(ImportConfiguration.empty(ID).withSchema(SampleDataset.SCHEMA).configuration()
                .withMapping(SampleDataset.MAPPING).configuration()
                .withTransformations(SampleDataset.TRANSFORMATIONS).configuration()
                .withValidations(SampleDataset.VALIDATIONS, List.of()).configuration(), T0);
    }

    private void givenSession(SessionStatus status) {
        sessions.save(ImportSession.restore(ID, new SourceFile("customers.csv", SourceFileType.CSV, 100), status,
                T0, T0, 0L, SampleDataset.SOURCE));
        storage.save(ID, new ByteArrayInputStream("the rows come from the fake parser".getBytes()));
    }
}
