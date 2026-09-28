package com.universaldatatools.tools.importer.infrastructure.export;

import com.universaldatatools.core.schema.FieldType;
import com.universaldatatools.core.common.RowErrorCode;
import com.universaldatatools.tools.importer.domain.pipeline.ErrorStage;
import com.universaldatatools.tools.importer.domain.pipeline.ImportError;
import com.universaldatatools.tools.importer.domain.pipeline.RowResult;
import com.universaldatatools.tools.importer.domain.schema.TargetField;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** The sample rows of the BE-F10 plan (task 3). */
final class ExportSamples {

    static final List<TargetField> FIELDS = List.of(
            new TargetField("name", FieldType.STRING, true, 0),
            new TargetField("score", FieldType.NUMBER, false, 1),
            new TargetField("active", FieldType.BOOLEAN, false, 2),
            new TargetField("dob", FieldType.DATE, false, 3),
            new TargetField("note", FieldType.STRING, false, 4));

    static final RowResult ROW_2 = valid(2, values("name", "An", "score", new BigDecimal("10"), "active", true,
            "dob", "1990-12-25", "note", null));
    static final RowResult ROW_5 = valid(5, values("name", "Em", "score", new BigDecimal("7.5"), "active", false,
            "dob", "1991-01-02", "note", "x"));

    static final RowResult ROW_3 = new RowResult(3, false, values("email", "abc"), List.of(
            new ImportError(3, "email", ErrorStage.VALIDATION, "email", null, RowErrorCode.VALIDATION_EMAIL,
                    "Not a valid email address", " ABC ")));
    static final RowResult ROW_4 = new RowResult(4, false, values("dob", null, "score", "x"), List.of(
            new ImportError(4, "dob", ErrorStage.TRANSFORMATION, "dateFormat", 0, RowErrorCode.TRANSFORMATION_FAILED,
                    "Does not match pattern dd/MM/yyyy", "31/02/2024"),
            new ImportError(4, "score", ErrorStage.VALIDATION, "type", null, RowErrorCode.VALIDATION_TYPE,
                    "Not a number", "x")));

    private ExportSamples() {
    }

    static RowResult valid(int rowNumber, Map<String, Object> values) {
        return new RowResult(rowNumber, true, values, List.of());
    }

    static RowResult invalid(ImportError error) {
        return new RowResult(error.rowNumber(), false, Map.of(), List.of(error));
    }

    static ImportError error(String fieldName, String rule, String message, String sourceValue) {
        return new ImportError(7, fieldName, ErrorStage.VALIDATION, rule, null, RowErrorCode.VALIDATION_TYPE, message,
                sourceValue);
    }

    static Map<String, Object> values(Object... keysAndValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            values.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return values;
    }

    /** The first row, then a read failure, as when the disk goes away mid-download. */
    static Stream<RowResult> failingAfter(RowResult first) {
        return Stream.of(1, 2).map(i -> {
            if (i == 2) {
                throw new UncheckedIOException(new IOException("disk gone"));
            }
            return first;
        });
    }
}
