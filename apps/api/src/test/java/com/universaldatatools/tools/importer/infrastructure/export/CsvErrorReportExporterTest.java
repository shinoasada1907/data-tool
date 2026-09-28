package com.universaldatatools.tools.importer.infrastructure.export;

import com.universaldatatools.tools.importer.domain.pipeline.RowResult;
import com.universaldatatools.support.CsvTestReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static com.universaldatatools.tools.importer.infrastructure.export.ExportSamples.ROW_3;
import static com.universaldatatools.tools.importer.infrastructure.export.ExportSamples.ROW_4;
import static com.universaldatatools.tools.importer.infrastructure.export.ExportSamples.error;
import static com.universaldatatools.tools.importer.infrastructure.export.ExportSamples.invalid;
import static org.assertj.core.api.Assertions.assertThat;

class CsvErrorReportExporterTest {

    private final CsvErrorReportExporter exporter = new CsvErrorReportExporter();

    @Test
    void it_describes_a_csv_download() {
        assertThat(exporter.contentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(exporter.fileSuffix()).isEqualTo("-errors.csv");
    }

    @Test
    void no_errors_is_the_bom_and_the_header() throws IOException {
        assertThat(CsvTestReader.raw(write(Stream.empty())))
                .isEqualTo("rowNumber,fieldName,stage,rule,step,code,message,sourceValue\r\n");
    }

    @Test
    void one_line_per_error_in_row_then_error_order() throws IOException {
        List<List<String>> csv = CsvTestReader.read(write(Stream.of(ROW_3, ROW_4)));

        assertThat(csv).hasSize(4);
        assertThat(csv.get(1)).containsExactly("3", "email", "VALIDATION", "email", "", "VALIDATION_EMAIL",
                "Not a valid email address", " ABC ");
        assertThat(csv.get(2)).containsExactly("4", "dob", "TRANSFORMATION", "dateFormat", "0",
                "TRANSFORMATION_FAILED", "Does not match pattern dd/MM/yyyy", "31/02/2024");
        assertThat(csv.get(3)).containsExactly("4", "score", "VALIDATION", "type", "", "VALIDATION_TYPE",
                "Not a number", "x");
    }

    @Test
    void user_controlled_columns_are_escaped() throws IOException {
        assertThat(line(error("email", "type", "m", "+84901234567")).get(7)).isEqualTo("'+84901234567");
        assertThat(line(error("email", "type", "-bad", "v")).get(6)).isEqualTo("'-bad");
        assertThat(line(error("=cmd()", "type", "m", "v")).get(1)).isEqualTo("'=cmd()");
        assertThat(line(error("email", "-rule", "m", "v")).get(3)).isEqualTo("'-rule");
    }

    @Test
    void a_null_source_value_is_an_empty_cell() throws IOException {
        assertThat(line(error("email", "type", "m", null)).get(7)).isEmpty();
    }

    private List<String> line(com.universaldatatools.tools.importer.domain.pipeline.ImportError error) throws IOException {
        return CsvTestReader.read(write(Stream.of(invalid(error)))).get(1);
    }

    private byte[] write(Stream<RowResult> rows) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(rows, out);
        return out.toByteArray();
    }
}
