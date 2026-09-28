package com.universaldatatools.core.table;

import com.universaldatatools.core.validate.EmailAddresses;

import java.util.ArrayList;
import java.util.List;

/**
 * Profiles columns cell by cell in one pass, in constant memory per column (core-02 IO7). Typed sources (JSON,
 * XLSX) are profiled by cell kind; untyped ones (CSV) by what the text looks like ({@link TextShapes}).
 */
public final class ProfileBuilder {

    private static final int BOOLEAN = 1;
    private static final int NUMERIC = 2;
    private static final int DATE = 4;
    private static final int EMAIL = 8;
    private static final int ALL_TEXT_CANDIDATES = BOOLEAN | NUMERIC | DATE | EMAIL;

    private final List<ColumnState> columns = new ArrayList<>();

    public ProfileBuilder(int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            columns.add(new ColumnState());
        }
    }

    /** A column first seen after {@code emptyBefore} rows, which count as empty cells of it (JSON keys). */
    public void addColumn(long emptyBefore) {
        ColumnState column = new ColumnState();
        column.empty = emptyBefore;
        columns.add(column);
    }

    /** One cell; {@code kind} is {@code null} for an untyped source. */
    public void accept(int column, String text, CellKind kind) {
        columns.get(column).accept(text, kind);
    }

    public List<ColumnProfile> build() {
        return columns.stream().map(ColumnState::profile).toList();
    }

    private static final class ColumnState {

        private long empty;
        private long nonEmpty;
        private int maxLength;
        private int textCandidates = ALL_TEXT_CANDIDATES;
        /** Bit per {@link CellKind} seen; 0 while the source has shown no kind. */
        private int kinds;

        void accept(String text, CellKind kind) {
            if (text == null || text.isBlank()) {
                empty++;
                if (text != null) {
                    maxLength = Math.max(maxLength, text.codePointCount(0, text.length()));
                }
                return;
            }
            nonEmpty++;
            maxLength = Math.max(maxLength, text.codePointCount(0, text.length()));
            if (kind != null) {
                kinds |= 1 << kind.ordinal();
                // Text never becomes a number in a typed source: "123" stays a string.
                textCandidates &= kind == CellKind.TEXT ? EMAIL : 0;
            }
            if (textCandidates != 0) {
                textCandidates &= candidatesOf(text);
            }
        }

        ColumnProfile profile() {
            return new ColumnProfile(type(), empty, maxLength);
        }

        private InferredType type() {
            if (nonEmpty == 0) {
                return InferredType.EMPTY;
            }
            if (kinds != 0) {
                return switch (Integer.bitCount(kinds) == 1 ? CellKind.values()[Integer.numberOfTrailingZeros(kinds)]
                        : null) {
                    case NUMBER -> InferredType.NUMBER;
                    case BOOLEAN -> InferredType.BOOLEAN;
                    case DATE -> InferredType.DATE;
                    case TEXT -> (textCandidates & EMAIL) != 0 ? InferredType.EMAIL : InferredType.STRING;
                    case null -> InferredType.STRING;
                };
            }
            if ((textCandidates & BOOLEAN) != 0) {
                return InferredType.BOOLEAN;
            }
            if ((textCandidates & NUMERIC) != 0) {
                return InferredType.NUMBER;
            }
            if ((textCandidates & DATE) != 0) {
                return InferredType.DATE;
            }
            return (textCandidates & EMAIL) != 0 ? InferredType.EMAIL : InferredType.STRING;
        }

        private int candidatesOf(String text) {
            int result = 0;
            if (TextShapes.isBoolean(text)) {
                result |= BOOLEAN;
            }
            if ((textCandidates & NUMERIC) != 0 && TextShapes.isNumber(text)) {
                result |= NUMERIC;
            }
            if ((textCandidates & DATE) != 0 && TextShapes.isIsoDate(text)) {
                result |= DATE;
            }
            if ((textCandidates & EMAIL) != 0 && EmailAddresses.isValid(text)) {
                result |= EMAIL;
            }
            return result;
        }
    }
}
