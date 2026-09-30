package com.universaldatatools.core.format.xlsx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class XlsxCellValuesTest {

    @ParameterizedTest(name = "[{index}] {0} id={1} {2} → {4}")
    @CsvSource(delimiter = '|', textBlock = """
            123                | 0   | General             | false | 123
            8.4901234567E10    | 0   | General             | false | 84901234567
            1E-3               | 0   | General             | false | 0.001
            0.074999999999999997 | 10 | 0.00%              | false | 0.075
            2750000.5          | 3   | '#,##0'             | false | 2750000.5
            1500000            | 3   | '#,##0'             | false | 1500000
            0.30000000000000004 | 0  | General             | false | 0.3
            45351              | 14  | m/d/yyyy            | false | 2024-02-29
            45651.573263888902 | 165 | yyyy-mm-dd hh:mm:ss | false | 2024-12-25T13:45:30
            45651.99999999     | 14  | m/d/yyyy            | false | 2024-12-26
            0.5625             | 20  | h:mm                | false | 13:30:00
            0.5732638889       | 21  | hh:mm:ss            | false | 13:45:30
            45651.5625         | 20  | h:mm                | false | 13:30:00
            0.99999999         | 21  | hh:mm:ss            | false | 00:00:00
            43830              | 164 | dd/mm/yyyy          | true  | 2024-01-01
            """)
    void numbers_become_plain_decimals_dates_or_times(String raw, int formatId, String format, boolean date1904,
                                                     String expected) {
        assertThat(XlsxCellValues.number(raw, formatId, format, date1904)).isEqualTo(expected);
    }

    @Test
    void serials_count_days_from_the_epoch_of_the_date_system() {
        assertThat(XlsxCellValues.serialToIso(new BigDecimal("45292"), false)).isEqualTo("2024-01-01");
        assertThat(XlsxCellValues.serialToIso(new BigDecimal("43830"), true)).isEqualTo("2024-01-01");
    }
}
