package com.universaldatatools.core.schema;

import java.util.Arrays;
import java.util.Optional;

/** Data type of a target field. The lowercase {@link #code()} is the API and storage value. */
public enum FieldType {
    STRING("string"), NUMBER("number"), BOOLEAN("boolean"), DATE("date"), EMAIL("email");

    private final String code;

    FieldType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Exact match only: {@code "String"} is not a type. */
    public static Optional<FieldType> fromCode(String code) {
        return Arrays.stream(values()).filter(type -> type.code.equals(code)).findFirst();
    }
}
