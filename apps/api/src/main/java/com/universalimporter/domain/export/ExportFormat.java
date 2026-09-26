package com.universalimporter.domain.export;

import com.universalimporter.domain.common.DomainException;
import com.universalimporter.domain.common.ErrorCode;

import java.util.Locale;

/** File format of the valid rows export (design F10-D7). */
public enum ExportFormat {
    JSON,
    CSV;

    /**
     * @throws DomainException {@code REQUEST_INVALID} when {@code raw} is missing or neither json nor csv
     */
    public static ExportFormat parse(String raw) {
        if (raw != null) {
            switch (raw.strip().toLowerCase(Locale.ROOT)) {
                case "json" -> {
                    return JSON;
                }
                case "csv" -> {
                    return CSV;
                }
                default -> {
                }
            }
        }
        throw new DomainException(ErrorCode.REQUEST_INVALID, "format must be json or csv.");
    }
}
