package com.universaldatatools.tools.importer.infrastructure.export;

import com.universaldatatools.core.schema.FieldType;
import com.universaldatatools.tools.importer.domain.export.ExportFormat;
import com.universaldatatools.tools.importer.domain.pipeline.RowResult;
import com.universaldatatools.tools.importer.domain.schema.TargetField;
import com.universaldatatools.support.CsvTestReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

class CsvValidRowsExporterTest {

    private final CsvValidRowsExporter exporter = new CsvValidRowsExporter();

    @Test
    void it_describes_a_csv_download() {
        assertThat(exporter.format()).isEqualTo(ExportFormat.CSV);
        assertThat(exporter.contentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(exporter.fileSuffix()).isEqualTo("-valid.csv");
    }

    @Test
    void no_rows_is_the_bom_and_the_header() throws IOException {
        byte[] csv = write(FIELDS, Stream.empty());

        assertThat(CsvTestReader.raw(csv)).isEqualTo("name,score,active,dob,note\r\n");
        assertThat(CsvTestReader.read(csv)).hasSize(1);
    }

    @Test
    void a_row_reads_back_with_plain_values_and_an_empty_cell_for_null() throws IOException {
        byte[] csv = write(FIELDS, Stream.of(ROW_2));

        assertThat(CsvTestReader.read(csv).get(1)).containsExactly("An", "10", "true", "1990-12-25", "");
        assertThat(CsvTestReader.raw(csv)).endsWith("\r\n");
    }

    @Test
    void commas_and_quotes_are_quoted_as_rfc_4180() throws IOException {
        byte[] csv = write(FIELDS, Stream.of(valid(2, values("name", "Nguyen, An", "note", "say \"hi\""))));

        assertThat(CsvTestReader.raw(csv)).contains("\"Nguyen, An\"", "\"say \"\"hi\"\"\"");
        assertThat(CsvTestReader.read(csv).get(1)).containsExactly("Nguyen, An", "", "", "", "say \"hi\"");
    }

    @Test
    void text_starting_like_a_formula_is_escaped() throws IOException {
        byte[] csv = write(FIELDS, Stream.of(valid(2, values("name", "=SUM(A1:A2)", "note", "-abc"))));

        List<String> row = CsvTestReader.read(csv).get(1);
        assertThat(row.get(0)).isEqualTo("'=SUM(A1:A2)");
        assertThat(row.get(4)).isEqualTo("'-abc");
    }

    @Test
    void numbers_are_never_escaped_and_are_plain() throws IOException {
        byte[] csv = write(FIELDS, Stream.of(valid(2, values("score", new BigDecimal("-3"))),
                valid(3, values("score", new BigDecimal("1E+3")))));

        assertThat(CsvTestReader.read(csv).get(1).get(1)).isEqualTo("-3");
        assertThat(CsvTestReader.read(csv).get(2).get(1)).isEqualTo("1000");
    }

    @Test
    void a_line_break_inside_a_value_survives() throws IOException {
        byte[] csv = write(FIELDS, Stream.of(valid(2, values("note", "line1\nline2"))));

        assertThat(CsvTestReader.read(csv).get(1).get(4)).isEqualTo("line1\nline2");
    }

    @Test
    void email_values_are_escaped_too() throws IOException {
        List<TargetField> email = List.of(new TargetField("email", FieldType.EMAIL, true, 0));

        assertThat(CsvTestReader.read(write(email, Stream.of(valid(2, values("email", "=cmd@x.io"))))).get(1))
                .containsExactly("'=cmd@x.io");
    }

    @Test
    void header_names_are_escaped() throws IOException {
        List<TargetField> fields = List.of(new TargetField("=cmd()", FieldType.STRING, false, 0),
                new TargetField("score", FieldType.NUMBER, false, 1));

        List<List<String>> csv = CsvTestReader.read(write(fields,
                Stream.of(valid(2, values("=cmd()", "x", "score", new BigDecimal("1"))))));

        assertThat(csv.get(0)).containsExactly("'=cmd()", "score");
        assertThat(csv.get(1)).containsExactly("x", "1");
    }

    @Test
    void invalid_rows_are_left_out() throws IOException {
        List<List<String>> csv = CsvTestReader.read(write(FIELDS, Stream.of(ROW_2, ROW_3, ROW_5)));

        assertThat(csv).hasSize(3);
        assertThat(csv.get(1).get(0)).isEqualTo("An");
        assertThat(csv.get(2).get(0)).isEqualTo("Em");
    }

    @Test
    void a_failure_midway_propagates() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThat(catchThrowableOfType(UncheckedIOException.class,
                () -> exporter.write(FIELDS, ExportSamples.failingAfter(ROW_2), out))).isNotNull();
        assertThat(out.toString(StandardCharsets.UTF_8)).doesNotContain("\"code\"");
    }

    private byte[] write(List<TargetField> fields, Stream<RowResult> rows) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(fields, rows, out);
        return out.toByteArray();
    }
}
