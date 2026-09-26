package com.universalimporter.domain.pipeline;

import com.universalimporter.domain.common.RowErrorCode;

/**
 * One error of one field of one row (design P3).
 *
 * @param rule        the transformation type, the validation rule, or {@code "mapping"}
 * @param step        the transformation's {@code order}; {@code null} for validation and mapping
 * @param message     English, never containing the cell value
 * @param sourceValue the mapped value before any transformation
 */
public record ImportError(int rowNumber, String fieldName, ErrorStage stage, String rule, Integer step,
                          RowErrorCode code, String message, String sourceValue) {
}
