package com.universaldatatools.core.table;

import java.util.Arrays;
import java.util.List;

/** The {@link CellKind} of each cell of a row, one byte per cell; {@code null} for an empty cell. Immutable. */
public final class CellKinds {

    private static final CellKind[] KINDS = CellKind.values();
    private static final byte NONE = -1;

    private final byte[] kinds;

    private CellKinds(byte[] kinds) {
        this.kinds = kinds;
    }

    /** {@code kinds} may hold {@code null} for empty cells. */
    public static CellKinds of(List<CellKind> kinds) {
        byte[] bytes = new byte[kinds.size()];
        for (int i = 0; i < bytes.length; i++) {
            CellKind kind = kinds.get(i);
            bytes[i] = kind == null ? NONE : (byte) kind.ordinal();
        }
        return new CellKinds(bytes);
    }

    /** {@code null} for an empty cell and past the last cell. */
    public CellKind get(int index) {
        return index < kinds.length && kinds[index] != NONE ? KINDS[kinds[index]] : null;
    }

    public int size() {
        return kinds.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CellKinds that && Arrays.equals(kinds, that.kinds);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(kinds);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < kinds.length; i++) {
            text.append(i == 0 ? "" : ", ").append(get(i));
        }
        return text.append(']').toString();
    }
}
