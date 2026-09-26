package com.universalimporter.domain.validation;

import com.universalimporter.domain.common.RowErrorCode;

/**
 * The first rule a field failed on one row.
 *
 * @param rule    {@code "required"}, {@code "type"}, {@code "email"} or {@code "unique"}
 * @param message English, never containing the cell value
 */
public record ValidationFailure(String rule, RowErrorCode code, String message) {
}
