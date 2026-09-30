package com.universaldatatools.core.validate;

import com.universaldatatools.core.common.RowErrorCode;
import com.universaldatatools.core.schema.Converted;
import com.universaldatatools.core.schema.SchemaField;
import com.universaldatatools.core.schema.TypeConverter;

/**
 * Checks a value against its field type and converts it to the real type, through {@link TypeConverter} (design
 * V3, core-03 SR4). Nothing is trimmed, so put a {@code trim} transformation first where needed.
 */
public final class TypeRule implements ValidationRule {

    private final TypeConverter converter = new TypeConverter();

    @Override
    public String type() {
        return "type";
    }

    @Override
    public RowErrorCode code() {
        return RowErrorCode.VALIDATION_TYPE;
    }

    @Override
    public ValidationResult validate(Object value, ValidationContext context) {
        SchemaField field = new SchemaField(context.fieldName(), context.fieldType(), false, context.constraints());
        return switch (converter.convert((String) value, field)) {
            case Converted.Ok ok -> new ValidationResult.Valid(ok.value());
            case Converted.Failed failed -> new ValidationResult.Invalid(failed.code(), failed.message());
        };
    }
}
