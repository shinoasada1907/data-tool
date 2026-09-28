package com.universaldatatools.tools.importer.application.importsession;
import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.tools.importer.domain.config.ImportConfiguration;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;
import com.universaldatatools.tools.importer.domain.importsession.SourceSchema;
import com.universaldatatools.tools.importer.domain.schema.FieldSpec;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;
import com.universaldatatools.core.table.SourceParser;
import com.universaldatatools.support.FakeSourceParser;
import com.universaldatatools.support.InMemoryFileStorage;
import com.universaldatatools.support.InMemoryImportConfigurationRepository;
import com.universaldatatools.support.InMemoryImportSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportSessionServiceTest {

    /** The clock ticks in nanoseconds; stored timestamps keep microseconds, as PostgreSQL does. */
    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00.123456789Z");
    private static final Instant NOW_IN_MICROS = Instant.parse("2026-09-25T10:00:00.123456Z");

    private static final SourceSchema SCHEMA =
            new SourceSchema(List.of(new Column(0, "name"), new Column(1, "email")), 2, null);

    private final InMemoryImportSessionRepository repository = new InMemoryImportSessionRepository();
    private final InMemoryImportConfigurationRepository configurations = new InMemoryImportConfigurationRepository();
    private final InMemoryFileStorage storage = new InMemoryFileStorage();
    /** No parser at all: the F01 behaviour, where an upload stops at UPLOADED. */
    private final ImportSessionService service = serviceWith();

    private ImportSessionService serviceWith(SourceParser... parsers) {
        return new ImportSessionService(repository, configurations, storage, Clock.fixed(NOW, ZoneOffset.UTC),
                new SourceParsers(List.of(parsers)));
    }

    @Test
    void upload_with_a_parser_inspects_the_stored_file_and_moves_the_session_to_configuring() {
        FakeSourceParser csv = FakeSourceParser.forType(DataFormat.CSV).returning(SCHEMA);

        ImportSession session = serviceWith(csv).upload("customers.csv", content("name,email\nAn,an@x.com\n")).session();

        assertThat(session.status()).isEqualTo(SessionStatus.CONFIGURING);
        assertThat(session.sourceSchema()).contains(SCHEMA);
        assertThat(repository.findById(session.id()).orElseThrow().sourceSchema()).contains(SCHEMA);
        assertThat(csv.inspectedContent()).isEqualTo(storage.content(session.id()));
    }

    @Test
    void a_file_the_parser_rejects_leaves_nothing_behind() {
        DomainException parseError = new DomainException(ErrorCode.FILE_PARSE_ERROR, "CSV syntax error near row 2.");
        ImportSessionService service = serviceWith(FakeSourceParser.forType(DataFormat.CSV).failingWith(parseError));

        assertThatThrownBy(() -> service.upload("broken.csv", content("a,b\n\"x\n"))).isSameAs(parseError);
        assertThat(storage.isEmpty()).isTrue();
        assertThat(repository.isEmpty()).isTrue();
    }

    @Test
    void an_unexpected_parser_failure_also_removes_the_stored_file() {
        IllegalStateException bug = new IllegalStateException("parser bug");
        ImportSessionService service = serviceWith(FakeSourceParser.forType(DataFormat.CSV).failingWith(bug));

        assertThatThrownBy(() -> service.upload("a.csv", content("a"))).isSameAs(bug);
        assertThat(storage.isEmpty()).isTrue();
    }

    @Test
    void without_a_parser_for_the_type_the_session_stays_uploaded() {
        ImportSessionService service = serviceWith(FakeSourceParser.forType(DataFormat.CSV).returning(SCHEMA));

        ImportSession session = service.upload("a.xlsx", new ByteArrayResource(new byte[]{0x50, 0x4B, 0x03, 0x04})).session();

        assertThat(session.status()).isEqualTo(SessionStatus.UPLOADED);
        assertThat(session.sourceSchema()).isEmpty();
    }

    @Test
    void an_inspected_file_is_still_removed_when_the_session_cannot_be_saved() {
        ImportSessionService service = serviceWith(FakeSourceParser.forType(DataFormat.CSV).returning(SCHEMA));
        repository.failOnSave();

        assertThatThrownBy(() -> service.upload("a.csv", content("a"))).hasMessage("database is down");
        assertThat(storage.isEmpty()).isTrue();
    }

    @Test
    void upload_stores_the_file_and_creates_an_uploaded_session() {
        ImportSession session = service.upload("customers.csv", content("a,b\n1,2")).session();

        assertThat(session.status()).isEqualTo(SessionStatus.UPLOADED);
        assertThat(session.sourceFile().fileType()).isEqualTo(DataFormat.CSV);
        assertThat(session.sourceFile().sizeBytes()).isEqualTo(7);
        assertThat(session.sourceFile().originalFileName()).isEqualTo("customers.csv");
        assertThat(session.createdAt()).isEqualTo(NOW_IN_MICROS);
        assertThat(storage.content(session.id())).isEqualTo("a,b\n1,2".getBytes(StandardCharsets.UTF_8));
        assertThat(repository.findById(session.id())).isPresent();
    }

    @Test
    void upload_keeps_only_the_sanitized_base_name() {
        ImportSession session = service.upload("../x.csv", content("a")).session();

        assertThat(session.sourceFile().originalFileName()).isEqualTo("x.csv");
    }

    @Test
    void unsupported_file_is_rejected_before_anything_is_stored() {
        assertThatThrownBy(() -> service.upload("data.xls", content("a")))
                .isInstanceOfSatisfying(DomainException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.FILE_UNSUPPORTED));
        assertThat(storage.isEmpty()).isTrue();
        assertThat(repository.isEmpty()).isTrue();
    }

    @Test
    void empty_file_is_rejected_before_anything_is_stored() {
        assertThatThrownBy(() -> service.upload("e.csv", content("")))
                .isInstanceOfSatisfying(DomainException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.FILE_EMPTY));
        assertThat(storage.isEmpty()).isTrue();
        assertThat(repository.isEmpty()).isTrue();
    }

    @Test
    void stored_file_is_removed_when_the_session_cannot_be_saved() {
        repository.failOnSave();

        assertThatThrownBy(() -> service.upload("a.csv", content("a")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database is down");
        assertThat(storage.isEmpty()).isTrue();
    }

    @Test
    void upload_comes_with_an_empty_configuration_that_is_not_ready() {
        SessionDetails details = serviceWith(FakeSourceParser.forType(DataFormat.CSV).returning(SCHEMA))
                .upload("customers.csv", content("name,email\nAn,an@x.com\n"));

        assertThat(details.configuration().sessionId()).isEqualTo(details.session().id());
        assertThat(details.configuration().schema().isEmpty()).isTrue();
        assertThat(details.readiness().ready()).isFalse();
        assertThat(details.readiness().issues()).extracting(ProblemItem::code).containsExactly("SCHEMA_EMPTY");
    }

    @Test
    void details_returns_the_stored_session_and_configuration() {
        ImportSession uploaded = service.upload("customers.csv", content("a,b")).session();
        TargetSchema schema = TargetSchema.define(List.of(new FieldSpec("email", "email", false, 0)));
        configurations.save(ImportConfiguration.empty(uploaded.id()).withSchema(schema).configuration(), NOW);

        SessionDetails found = service.details(uploaded.id());

        assertThat(found.session().id()).isEqualTo(uploaded.id());
        assertThat(found.session().status()).isEqualTo(SessionStatus.UPLOADED);
        assertThat(found.configuration().schema()).isEqualTo(schema);
        assertThat(found.readiness().ready()).isTrue();
    }

    @Test
    void details_of_an_unknown_id_is_session_not_found() {
        assertThatThrownBy(() -> service.details(UUID.fromString("11111111-2222-3333-4444-555555555555")))
                .isInstanceOfSatisfying(DomainException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_NOT_FOUND));
    }

    private static ByteArrayResource content(String text) {
        return new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8));
    }
}
