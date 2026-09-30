package com.universaldatatools.core.format.xlsx;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelDateFormatsTest {

    @ParameterizedTest(name = "[{index}] id={0} format={1} → {2}")
    @CsvSource(delimiter = '|', nullValues = "NULL", textBlock = """
            14   | m/d/yyyy               | true
            14   | mm-dd-yy               | true
            22   | m/d/yy h:mm            | true
            45   | mm:ss                  | true
            0    | General                | false
            2    | 0.00                   | false
            49   | @                      | false
            164  | dd/mm/yyyy             | true
            165  | yyyy\\-mm\\-dd\\ hh:mm:ss | true
            166  | [$-409]mmmm d, yyyy    | true
            167  | '#,##0 "VND"'          | false
            168  | '0.00 "dm"'            | false
            169  | [Red]0.00              | false
            170  | [h]:mm                 | true
            171  | \\d0.0                 | false
            NULL | NULL                   | false
            """)
    void recognises_date_and_time_formats(Integer formatId, String formatString, boolean expected) {
        assertThat(ExcelDateFormats.isDateFormat(formatId, formatString)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] id={0} format={1} → {2}")
    @CsvSource(delimiter = '|', nullValues = "NULL", textBlock = """
            20   | h:mm                   | true
            21   | h:mm:ss                | true
            45   | mm:ss                  | true
            170  | [h]:mm                 | true
            172  | hh:mm AM/PM            | true
            20   | NULL                   | true
            14   | m/d/yyyy               | false
            165  | yyyy-mm-dd hh:mm:ss    | false
            164  | dd/mm/yyyy             | false
            173  | mmmm                   | false
            0    | General                | false
            """)
    void recognises_formats_that_show_only_a_time_of_day(Integer formatId, String formatString, boolean expected) {
        assertThat(ExcelDateFormats.isTimeOnlyFormat(formatId, formatString)).isEqualTo(expected);
    }
}
