package com.universaldatatools.core.validate;

import com.universaldatatools.core.schema.FieldConstraints;
import com.universaldatatools.core.schema.FieldType;

/**
 * What a rule knows besides the value.
 *
 * @param rowNumber   row number as a spreadsheet shows it (the header is row 1)
 * @param unique      values seen so far in this run; only the {@code unique} rule uses it
 * @param constraints the field's constraints; the {@code type} rule reads the date {@code format} from them
 */
public record ValidationContext(String fieldName, FieldType fieldType, int rowNumber, UniqueIndex unique,
                                FieldConstraints constraints) {

    /** A field without constraints, as every importer field is. */
    public ValidationContext(String fieldName, FieldType fieldType, int rowNumber, UniqueIndex unique) {
        this(fieldName, fieldType, rowNumber, unique, FieldConstraints.NONE);
    }
}
