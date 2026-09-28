package com.universaldatatools.tools.importer.application.importsession;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.table.SourceSchema;
import com.universaldatatools.support.FakeSourceParser;
import com.universaldatatools.support.InMemoryFileStorage;
import com.universaldatatools.support.InMemoryImportSessionRepository;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.SessionStatus;
import com.universaldatatools.tools.importer.domain.importsession.SourceFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourcePreviewServiceTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final Instant T0 = Instant.parse("2026-09-25T10:00:00Z");
    private static final SourceFile FILE = new SourceFile("customers.csv", DataFormat.CSV, 42);
    private static final List<Column> COLUMNS = List.of(new Column(0, "name"), new Column(1, "email"));
    private static final List<Row> ROWS = List.of(
            new Row(2, List.of("An", "an@x.com")),
            new Row(3, List.of("Binh", "binh@x.com")),
            new Row(5, List.of("Chi", "chi@x.com")));

    private final InMemoryImportSessionRepository repository = new InMemoryImportSessionRepository();
    private final InMemoryFileStorage storage = new InMemoryFileStorage();
    private final FakeSourceParser parser = FakeSourceParser.forType(DataFormat.CSV).withRows(ROWS);
    private final SourcePreviewService service =
            new SourcePreviewService(repository, storage, new SourceParsers(List.of(parser)));

    @BeforeEach
    void storeTheFile() {
        storage.save(ID, new ByteArrayInputStream("name,email\n".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void preview_returns_the_first_rows_with_columns_and_total_from_the_stored_schema() {
        repository.save(inspectedSession());

        SourcePreview preview = service.preview(ID, 2);

        assertThat(preview.sessionId()).isEqualTo(ID);
        assertThat(preview.fileType()).isEqualTo(DataFormat.CSV);
        assertThat(preview.sheetName()).isNull();
        assertThat(preview.columns()).isEqualTo(COLUMNS);
        assertThat(preview.rows()).containsExactly(ROWS.get(0), ROWS.get(1));
        assertThat(preview.previewLimit()).isEqualTo(2);
        assertThat(preview.totalRows()).isEqualTo(3);
    }

    @Test
    void a_limit_above_the_row_count_returns_every_row() {
        repository.save(inspectedSession());

        assertThat(service.preview(ID, 50).rows()).isEqualTo(ROWS);
    }

    @Test
    void the_row_stream_is_closed_after_the_preview() {
        repository.save(inspectedSession());

        service.preview(ID, 2);

        assertThat(parser.readStreamClosed()).isTrue();
    }

    @Test
    void a_session_whose_file_was_never_inspected_cannot_be_previewed() {
        repository.save(ImportSession.create(ID, FILE, T0));

        assertThatThrownBy(() -> service.preview(ID, 50))
                .isInstanceOfSatisfying(DomainException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_STATE_INVALID);
                    assertThat(ex.getMessage()).isEqualTo("Source file has not been inspected.");
                });
    }

    @Test
    void an_unknown_session_is_not_found() {
        assertThatThrownBy(() -> service.preview(UUID.fromString("11111111-2222-3333-4444-555555555555"), 50))
                .isInstanceOfSatisfying(DomainException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.SESSION_NOT_FOUND));
    }

    private static ImportSession inspectedSession() {
        return ImportSession.restore(ID, FILE, SessionStatus.CONFIGURING, T0, T0, 0L,
                new SourceSchema(COLUMNS, 3, null));
    }
}
