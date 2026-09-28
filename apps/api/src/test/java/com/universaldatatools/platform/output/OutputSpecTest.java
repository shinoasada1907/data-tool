package com.universaldatatools.platform.output;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.core.table.Delimiter;
import com.universaldatatools.core.table.Typing;
import com.universaldatatools.core.table.WriteOptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** core-04 task 13: output options of the toolbox contract. */
class OutputSpecTest {

    @Test
    void csv_defaults() {
        OutputSpec spec = OutputSpec.from(new OutputDto("csv", null, null, null), "Data");

        assertThat(spec.format()).isEqualTo(DataFormat.CSV);
        assertThat(spec.options()).isEqualTo(WriteOptions.csvDefaults());
    }

    @Test
    void csv_options() {
        OutputSpec spec = OutputSpec.from(new OutputDto("CSV",
                new OutputDto.CsvOptions("semicolon", false, false, false), null, null), null);

        assertThat(spec.options().delimiter()).isEqualTo(Delimiter.SEMICOLON);
        assertThat(spec.options().header()).isFalse();
        assertThat(spec.options().bom()).isFalse();
        assertThat(spec.options().formulaGuard()).isFalse();
    }

    @Test
    void json_and_xlsx_options() {
        OutputSpec json = OutputSpec.from(new OutputDto("JSON", null, new OutputDto.JsonOptions(true, "string"), null),
                null);
        assertThat(json.options().pretty()).isTrue();
        assertThat(json.options().typing()).isEqualTo(Typing.STRING);

        OutputSpec xlsx = OutputSpec.from(new OutputDto("XLSX", null, null, new OutputDto.XlsxOptions("Q1", null)),
                "Data");
        assertThat(xlsx.options().sheetName()).isEqualTo("Q1");
        assertThat(xlsx.options().typing()).isEqualTo(Typing.PRESERVE);
        assertThat(OutputSpec.from(new OutputDto("XLSX", null, null, null), "Data").options().sheetName())
                .isEqualTo("Data");
    }

    @Test
    void mistakes_are_named() {
        assertInvalid(new OutputDto(null, null, null, null), "output.format is required.");
        assertInvalid(new OutputDto("xml", null, null, null), "output.format must be one of [CSV, XLSX, JSON].");
        assertInvalid(new OutputDto("JSON", new OutputDto.CsvOptions(null, null, null, null), null, null),
                "output.csv applies to CSV only.");
        assertInvalid(new OutputDto("JSON", null, new OutputDto.JsonOptions(null, "LOOSE"), null),
                "output.json.typing must be one of [PRESERVE, STRING, INFER].");
    }

    private static void assertInvalid(OutputDto dto, String message) {
        assertThatThrownBy(() -> OutputSpec.from(dto, null)).isInstanceOfSatisfying(DomainException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.CONFIG_INVALID);
            assertThat(e.items()).extracting(ProblemItem::message).contains(message);
        });
    }
}
