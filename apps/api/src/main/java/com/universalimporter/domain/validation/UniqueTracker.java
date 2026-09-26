package com.universalimporter.domain.validation;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Values seen by the {@code unique} rule during one run, per field (design V4). Two phases: while a row is checked
 * its values are only staged; {@link #commitRow} records them once the whole row turned out valid, and
 * {@link #discardRow} drops them when it did not, so an invalid row never blocks a later valid one.
 * <p>
 * Each row is bracketed: {@link #beginRow}, then {@link #commitRow} or {@link #discardRow}. Getting that wrong
 * throws at once instead of silently recording duplicates or wrong row numbers. Not thread-safe: one tracker per
 * run, used by one thread.
 */
public final class UniqueTracker {

    /** Field → canonical value → row that first recorded it. */
    private final Map<String, Map<Object, Integer>> recorded = new HashMap<>();
    private final Map<String, Set<Object>> staged = new LinkedHashMap<>();
    private Integer currentRow;

    /** @throws IllegalStateException when the previous row was neither committed nor discarded */
    public void beginRow(int rowNumber) {
        if (currentRow != null) {
            throw new IllegalStateException("Row " + currentRow + " was neither committed nor discarded");
        }
        currentRow = rowNumber;
    }

    public Optional<Integer> firstRowOf(String field, Object canonicalValue) {
        return Optional.ofNullable(recorded.getOrDefault(field, Map.of()).get(canonicalValue));
    }

    /** @throws IllegalStateException outside a row */
    public void stage(String field, Object canonicalValue) {
        requireRow();
        staged.computeIfAbsent(field, f -> new LinkedHashSet<>()).add(canonicalValue);
    }

    /** Records the current row's staged values; a value recorded earlier keeps its first row. */
    public void commitRow() {
        int row = requireRow();
        staged.forEach((field, values) -> {
            Map<Object, Integer> rows = recorded.computeIfAbsent(field, f -> new HashMap<>());
            values.forEach(value -> rows.putIfAbsent(value, row));
        });
        endRow();
    }

    public void discardRow() {
        requireRow();
        endRow();
    }

    /**
     * The form values are compared in: numbers by value ({@code 1.0}, {@code 1.00} and {@code 1} are equal),
     * everything else as it is, so strings stay case-sensitive. Numbers are at most 1000 characters (see
     * {@link TypeRule}), which keeps {@code stripTrailingZeros} cheap.
     */
    public static Object canonical(Object typedValue) {
        if (typedValue instanceof BigDecimal number) {
            return number.signum() == 0 ? BigDecimal.ZERO : number.stripTrailingZeros();
        }
        return typedValue;
    }

    private int requireRow() {
        if (currentRow == null) {
            throw new IllegalStateException("No row has begun");
        }
        return currentRow;
    }

    private void endRow() {
        staged.clear();
        currentRow = null;
    }
}
