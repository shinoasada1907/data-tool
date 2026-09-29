package com.universaldatatools.core.validate;

import com.universaldatatools.core.table.Hash128;
import com.universaldatatools.core.table.KeyHasher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Values seen by the {@code unique} rule during one run, for every field (core-03 SR7, V0.1 design V4). Keys are
 * 128-bit hashes of field name, kind and canonical value, so memory does not grow with cell length.
 * <p>
 * Each row is bracketed: {@link #beginRow}, then {@link #commitRow} or {@link #discardRow}. With
 * {@link UniqueScope#VALID_ROWS} a row's values are only staged until it is committed; with
 * {@link UniqueScope#ALL_ROWS} they count at once and commit and discard only close the row. Getting the brackets
 * wrong throws at once instead of silently recording wrong row numbers. Not thread-safe: one index per run.
 */
public final class UniqueIndex {

    private final UniqueScope scope;
    private final KeyHasher hasher = new KeyHasher();
    private final Map<Hash128, Integer> firstRows = new HashMap<>();
    private final List<Hash128> staged = new ArrayList<>();
    private int currentRow = -1;

    public UniqueIndex(UniqueScope scope) {
        this.scope = scope;
    }

    /** @throws IllegalStateException when the previous row was neither committed nor discarded */
    public void beginRow(int rowNumber) {
        if (currentRow >= 0) {
            throw new IllegalStateException("Row " + currentRow + " was neither committed nor discarded");
        }
        currentRow = rowNumber;
    }

    /** Records the current row's staged values; a value recorded earlier keeps its first row. */
    public void commitRow() {
        int row = requireRow();
        staged.forEach(key -> firstRows.putIfAbsent(key, row));
        endRow();
    }

    public void discardRow() {
        requireRow();
        endRow();
    }

    /**
     * The key of a typed value. Numbers compare by value ({@code 1.0}, {@code 1.00} and {@code 1} are equal),
     * strings case-sensitively, dates by day; the kind tag keeps the number 1 and the string "1" apart.
     */
    public Hash128 key(String field, Object typedValue) {
        return hasher.hash(List.of(field, kind(typedValue), canonical(typedValue)));
    }

    public OptionalInt firstRowOf(Hash128 key) {
        Integer row = firstRows.get(key);
        return row == null ? OptionalInt.empty() : OptionalInt.of(row);
    }

    /** @throws IllegalStateException outside a row */
    public void record(Hash128 key) {
        int row = requireRow();
        if (scope == UniqueScope.ALL_ROWS) {
            firstRows.putIfAbsent(key, row);
        } else {
            staged.add(key);
        }
    }

    private static String kind(Object value) {
        return switch (value) {
            case BigDecimal number -> "N";
            case Boolean bool -> "B";
            case LocalDate date -> "D";
            default -> "S";
        };
    }

    /** Numbers are at most 1000 characters (see {@code TypeConverter}), which keeps the plain form small. */
    private static String canonical(Object value) {
        if (value instanceof BigDecimal number) {
            return number.signum() == 0 ? "0" : number.stripTrailingZeros().toPlainString();
        }
        return value.toString();
    }

    private int requireRow() {
        if (currentRow < 0) {
            throw new IllegalStateException("No row has begun");
        }
        return currentRow;
    }

    private void endRow() {
        staged.clear();
        currentRow = -1;
    }
}
