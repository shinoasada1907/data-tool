package com.universaldatatools.tools.importer.domain.export;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ExportFormatTest {

    @Test
    void the_format_is_json_or_csv_in_any_case() {
        assertThat(ExportFormat.parse("JSON")).isEqualTo(ExportFormat.JSON);
        assertThat(ExportFormat.parse("csv")).isEqualTo(ExportFormat.CSV);
    }

    @Test
    void a_missing_or_unknown_format_is_a_bad_request() {
        assertThat(catchThrowableOfType(DomainException.class, () -> ExportFormat.parse(null)).code())
                .isEqualTo(ErrorCode.REQUEST_INVALID);
        assertThat(catchThrowableOfType(DomainException.class, () -> ExportFormat.parse("xml")).code())
                .isEqualTo(ErrorCode.REQUEST_INVALID);
    }
}
