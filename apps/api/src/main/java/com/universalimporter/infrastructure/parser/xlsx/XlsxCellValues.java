package com.universalimporter.infrastructure.parser.xlsx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Turns numeric cell values into the strings of design X3 / D9. */
final class XlsxCellValues {

    private static final long SECONDS_PER_DAY = 86_400;
    /** Day 0 of Excel's 1900 date system; correct for every date after Excel's fictitious 1900-02-29. */
    private static final LocalDate EPOCH_1900 = LocalDate.of(1899, 12, 30);
    private static final LocalDate EPOCH_1904 = LocalDate.of(1904, 1, 1);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private XlsxCellValues() {
    }

    /**
     * {@code raw} is the number exactly as written in the sheet XML. Never goes through {@code double},
     * so long numbers such as phone numbers keep every digit.
     */
    static String number(String raw, Integer formatId, String formatString, boolean date1904) {
        BigDecimal value = new BigDecimal(raw);
        if (ExcelDateFormats.isTimeOnlyFormat(formatId, formatString)) {
            return timeOfDay(value);
        }
        if (ExcelDateFormats.isDateFormat(formatId, formatString)) {
            return serialToIso(value, date1904);
        }
        return value.toPlainString();
    }

    /** {@code yyyy-MM-dd}, or {@code yyyy-MM-dd'T'HH:mm:ss} when the time is not midnight (rounded to seconds). */
    static String serialToIso(BigDecimal serial, boolean date1904) {
        long seconds = toSeconds(serial);
        LocalDateTime dateTime = (date1904 ? EPOCH_1904 : EPOCH_1900).atStartOfDay().plusSeconds(seconds);
        return dateTime.toLocalTime().equals(LocalTime.MIDNIGHT)
                ? dateTime.toLocalDate().toString()
                : dateTime.format(DATE_TIME);
    }

    /** Time of day from the fractional part of the serial; the date part, if any, is dropped (decided 2026-09-25). */
    private static String timeOfDay(BigDecimal serial) {
        long secondOfDay = Math.floorMod(toSeconds(serial), SECONDS_PER_DAY);
        return LocalTime.ofSecondOfDay(secondOfDay).format(TIME);
    }

    private static long toSeconds(BigDecimal serial) {
        return serial.multiply(BigDecimal.valueOf(SECONDS_PER_DAY)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
