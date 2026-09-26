package com.universalimporter.domain.validation;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Values seen by the {@code unique} rule during one run, per field (design V4). Two phases: a row's values are only
 * staged while the row is checked, and recorded by {@link #commitRow} once the whole row turned out valid. A row
 * with any error calls {@link #discardRow}, so it never blocks a later valid row with the same value. Not
 * thread-safe: one tracker per run, used by one thread.
 */
public final class UniqueTracker {

    /** Field → canonical value → row that first recorded it. */
    private final Map<String, Map<Object, Integer>> recorded = new HashMap<>();
    private final Map<String, Set<Object>> staged = new LinkedHashMap<>();

    public Optional<Integer> firstRowOf(String field, Object canonicalValue) {
        return Optional.ofNullable(recorded.getOrDefault(field, Map.of()).get(canonicalValue));
    }

    public void stage(String field, Object canonicalValue) {
        staged.computeIfAbsent(field, f -> new LinkedHashSet<>()).add(canonicalValue);
    }

    /** Records the staged values under {@code rowNumber}; a value recorded earlier keeps its first row. */
    public void commitRow(int rowNumber) {
        staged.forEach((field, values) -> {
            Map<Object, Integer> rows = recorded.computeIfAbsent(field, f -> new HashMap<>());
            values.forEach(value -> rows.putIfAbsent(value, rowNumber));
        });
        staged.clear();
    }

    public void discardRow() {
        staged.clear();
    }

    /**
     * The form values are compared in: numbers by value ({@code 1.0}, {@code 1.00} and {@code 1} are equal),
     * everything else as it is, so strings stay case-sensitive.
     */
    public static Object canonical(Object typedValue) {
        if (typedValue instanceof BigDecimal number) {
            // stripTrailingZeros keeps 100 as 1E+2, which still equals 100.0 stripped the same way.
            return number.signum() == 0 ? BigDecimal.ZERO : number.stripTrailingZeros();
        }
        return typedValue;
    }
}
