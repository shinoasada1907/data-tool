package com.universalimporter.domain.pipeline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The outcome of one source row (design P3).
 *
 * @param values every schema field in schema order: converted values ({@code BigDecimal}, {@code Boolean},
 *               {@code LocalDate}, {@code String}) for a valid row; the transformed strings for an invalid one,
 *               {@code null} where a transformation failed. Unmapped and empty optional fields are {@code null}.
 * @param errors in schema order, at most one per field
 */
public record RowResult(int rowNumber, boolean valid, Map<String, Object> values, List<ImportError> errors) {

    public RowResult {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        errors = List.copyOf(errors);
    }
}
