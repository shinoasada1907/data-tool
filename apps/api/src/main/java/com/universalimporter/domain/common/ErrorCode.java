package com.universalimporter.domain.common;

/**
 * API error codes (design D4). The name is the stable contract value sent as {@code code};
 * the HTTP status for each code lives in the api layer.
 */
public enum ErrorCode {
    REQUEST_INVALID,
    SESSION_NOT_FOUND,
    SESSION_NOT_READY,
    SESSION_STATE_INVALID,
    RESULT_NOT_AVAILABLE,
    FILE_TOO_LARGE,
    FILE_UNSUPPORTED,
    FILE_EMPTY,
    FILE_PARSE_ERROR,
    SCHEMA_INVALID,
    MAPPING_INVALID,
    SOURCE_COLUMN_NOT_FOUND,
    CONFIG_INVALID,
    EXPORT_FAILED,
    INTERNAL_ERROR
}
