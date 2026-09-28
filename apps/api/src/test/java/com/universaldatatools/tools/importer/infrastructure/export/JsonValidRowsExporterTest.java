package com.universaldatatools.tools.importer.infrastructure.export;

import com.universaldatatools.core.schema.FieldType;
import com.universaldatatools.tools.importer.domain.export.ExportFormat;
import com.universaldatatools.tools.importer.domain.pipeline.RowResult;
import com.universaldatatools.tools.importer.domain.schema.TargetField;
import org.junit.jupiter.api.Test;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static com.universaldatatools.tools.importer.infrastructure.export.ExportSamples.FIELDS;
import static com.universaldatatools.tools.importer.infrastructure.export.ExportSamples.ROW_2;
import static com.universaldatatools.tools.importer.infrastructure.export.ExportSamples.ROW_3;
import static com.universaldatatools.tools.importer.infrastructure.export.ExportSamples.ROW_5;
import static com.universaldatatools.tools.importer.infrastructure.export.ExportSamples.valid;
import static com.universaldatatools.tools.importer.infrastructure.export.ExportSamples.values;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class JsonValidRowsExporterTest {

    private static final String ROW_2_JSON = "{\"name\":\"An\",\"score\":10,\"active\":true,\"dob\":\"1990-12-25\",\"note\":null}";

    private final JsonValidRowsExporter exporter = new JsonValidRowsExporter(
            JsonMapper.builder().enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN).build());

    @Test
    void it_describes_a_json_download() {
        assertThat(exporter.format()).isEqualTo(ExportFormat.JSON);
        assertThat(exporter.contentType()).isEqualTo("application/json");
        assertThat(exporter.fileSuffix()).isEqualTo("-valid.json");
    }

    @Test
    void no_rows_is_an_empty_array() throws IOException {
        assertThat(write(FIELDS, Stream.empty())).isEqualTo("[]");
    }

    @Test
    void one_object_per_row_with_typed_values() throws IOException {
        assertThat(write(FIELDS, Stream.of(ROW_2))).isEqualTo("[" + ROW_2_JSON + "]");
        assertThat(write(FIELDS, Stream.of(ROW_2, ROW_5))).isEqualTo("[" + ROW_2_JSON
                + ",{\"name\":\"Em\",\"score\":7.5,\"active\":false,\"dob\":\"1991-01-02\",\"note\":\"x\"}]");
    }

    @Test
    void keys_follow_the_schema_not_the_map() throws IOException {
        RowResult reversed = valid(2, values("note", null, "dob", "1990-12-25", "active", true,
                "score", new BigDecimal("10"), "name", "An"));

        assertThat(write(FIELDS, Stream.of(reversed))).isEqualTo("[" + ROW_2_JSON + "]");
    }

    @Test
    void a_missing_value_is_null() throws IOException {
        RowResult noNote = valid(2, values("name", "An", "score", new BigDecimal("10"), "active", true,
                "dob", "1990-12-25"));

        assertThat(write(FIELDS, Stream.of(noNote))).isEqualTo("[" + ROW_2_JSON + "]");
    }

    @Test
    void small_numbers_are_plain() throws IOException {
        RowResult tiny = valid(2, values("score", new BigDecimal("0.0000001")));

        assertThat(write(FIELDS, Stream.of(tiny))).contains("0.0000001").doesNotContain("1E-7");
    }

    @Test
    void json_is_never_escaped_against_formulas() throws IOException {
        List<TargetField> formulaField = List.of(new TargetField("=cmd()", FieldType.STRING, false, 0));

        assertThat(write(formulaField, Stream.of(valid(2, values("=cmd()", "=HYPERLINK(\"x\")")))))
                .isEqualTo("[{\"=cmd()\":\"=HYPERLINK(\\\"x\\\")\"}]");
    }

    @Test
    void invalid_rows_are_left_out() throws IOException {
        String json = write(FIELDS, Stream.of(ROW_2, ROW_3, ROW_5));

        assertThat(json).contains("\"An\"", "\"Em\"").doesNotContain("abc");
    }

    @Test
    void a_failure_midway_propagates_and_leaves_no_closed_array_nor_error_body() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        UncheckedIOException ex = catchThrowableOfType(UncheckedIOException.class,
                () -> exporter.write(FIELDS, ExportSamples.failingAfter(ROW_2), out));

        assertThat(ex).isNotNull();
        String written = out.toString(StandardCharsets.UTF_8);
        assertThat(written).doesNotContain("\"code\"").doesNotEndWith("]");
    }

    @Test
    void the_output_stream_is_left_open() throws IOException {
        boolean[] closed = {false};
        OutputStream out = new ByteArrayOutputStream() {
            @Override
            public void close() {
                closed[0] = true;
            }
        };

        exporter.write(FIELDS, Stream.of(ROW_2), out);

        assertThat(closed[0]).isFalse();
    }

    private String write(List<TargetField> fields, Stream<RowResult> rows) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(fields, rows, out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
