package com.universaldatatools.platform.web;

import com.universaldatatools.core.common.ErrorCode;
import org.springframework.http.HttpStatus;

/** HTTP status for each API error code (design D4). No default branch: a new code must be mapped here. */
public final class ErrorHttpStatus {

    private ErrorHttpStatus() {
    }

    public static HttpStatus of(ErrorCode code) {
        return switch (code) {
            case REQUEST_INVALID -> HttpStatus.BAD_REQUEST;
            case SESSION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SESSION_NOT_READY, SESSION_STATE_INVALID, RESULT_NOT_AVAILABLE -> HttpStatus.CONFLICT;
            case FILE_TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case FILE_UNSUPPORTED -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case FILE_EMPTY, FILE_PARSE_ERROR, SCHEMA_INVALID, MAPPING_INVALID, SOURCE_COLUMN_NOT_FOUND,
                 CONFIG_INVALID -> HttpStatus.UNPROCESSABLE_CONTENT;
            case EXPORT_FAILED, INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
