package com.universalimporter.application.pipeline;

import com.universalimporter.application.common.SessionLocks;
import com.universalimporter.application.importsession.SourceParsers;
import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;
import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.config.ConfigHasher;
import com.universalimporter.domain.config.ImportConfiguration;
import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.SessionStatus;
import com.universalimporter.domain.importsession.SourceFile;
import com.universalimporter.domain.importsession.SourceFileType;
import com.universalimporter.domain.mapping.MappingConfig;
import com.universalimporter.domain.mapping.MappingStrategies;
import com.universalimporter.domain.pipeline.DefaultImportPipeline;
import com.universalimporter.domain.pipeline.SampleDataset;
import com.universalimporter.domain.transformation.TransformationEngine;
import com.universalimporter.domain.transformation.TransformationRegistry;
import com.universalimporter.domain.validation.FieldValidator;
import com.universalimporter.domain.validation.ValidationRegistry;
import com.universalimporter.support.FakeSourceParser;
import com.universalimporter.support.InMemoryFileStorage;
import com.universalimporter.support.InMemoryImportConfigurationRepository;
import com.universalimporter.support.InMemoryImportSessionRepository;
import com.universalimporter.support.InMemoryResultStore;
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
