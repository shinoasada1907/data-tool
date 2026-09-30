package com.universaldatatools.platform.web;

import com.universaldatatools.core.common.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The code → status table is part of the API contract (design D4) that the FE relies on. */
class ErrorHttpStatusTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "REQUEST_INVALID, 400",
            "SESSION_NOT_FOUND, 404",
            "SESSION_NOT_READY, 409",
            "SESSION_STATE_INVALID, 409",
            "RESULT_NOT_AVAILABLE, 409",
            "FILE_TOO_LARGE, 413",
            "FILE_UNSUPPORTED, 415",
            "FILE_EMPTY, 422",
            "FILE_PARSE_ERROR, 422",
            "SCHEMA_INVALID, 422",
            "MAPPING_INVALID, 422",
            "SOURCE_COLUMN_NOT_FOUND, 422",
            "CONFIG_INVALID, 422",
            "EXPORT_FAILED, 500",
            "INTERNAL_ERROR, 500"
    })
    void maps_each_code_to_its_contract_status(ErrorCode code, int expectedStatus) {
        assertThat(ErrorHttpStatus.of(code).value()).isEqualTo(expectedStatus);
    }
}
