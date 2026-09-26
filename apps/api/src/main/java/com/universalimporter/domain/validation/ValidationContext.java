package com.universalimporter.domain.validation;

import com.universalimporter.domain.schema.FieldType;

/**
 * What a rule knows besides the value.
 *
 * @param rowNumber     row number as a spreadsheet shows it (the header is row 1)
 * @param uniqueTracker values seen so far in this run; only the {@code unique} rule uses it
 */
public record ValidationContext(String fieldName, FieldType fieldType, int rowNumber, UniqueTracker uniqueTracker) {
}
