package com.universaldatatools.core.table;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;

import java.util.ArrayList;
import java.util.List;

/**
 * What every reader does while inspecting: counts rows and blank rows, profiles columns and enforces the
 * {@link ReadLimits}, failing at the first row past a limit (core-02 IO7, IO8).
 */
public final class TableScan {

    private final List<Column> columns;
    private final ReadLimits limits;
    private final ProfileBuilder profiles;
    private long rows;
    private long blankRows;

    /** @throws DomainException {@code LIMIT_EXCEEDED} when there are too many columns */
    public TableScan(List<Column> columns, ReadLimits limits) {
        this.limits = limits;
        checkColumns(columns.size());
        this.columns = new ArrayList<>(columns);
        this.profiles = new ProfileBuilder(columns.size());
    }

    /**
     * A column found after some rows were accepted, as JSON keys are; those rows count as empty cells of it.
     *
     * @throws DomainException {@code LIMIT_EXCEEDED} when there are now too many columns
     */
    public void addColumn(Column column) {
        checkColumns(columns.size() + 1);
        columns.add(column);
        profiles.addColumn(rows);
    }

    private void checkColumns(int count) {
        if (limits.maxColumns() > 0 && count > limits.maxColumns()) {
            throw new DomainException(ErrorCode.LIMIT_EXCEEDED,
                    "File has more than " + limits.maxColumns() + " columns.");
        }
    }

    public void blank() {
        blankRows++;
    }

    /** @throws DomainException {@code LIMIT_EXCEEDED} for one row too many or a cell too long */
    public void accept(Row row) {
        rows++;
        if (limits.maxRows() > 0 && rows > limits.maxRows()) {
            throw new DomainException(ErrorCode.LIMIT_EXCEEDED, "File has more than " + limits.maxRows() + " rows.");
        }
        for (int i = 0; i < columns.size(); i++) {
            String value = row.value(i);
            if (limits.maxCellLength() > 0 && value != null && value.length() > limits.maxCellLength()
                    && value.codePointCount(0, value.length()) > limits.maxCellLength()) {
                throw new DomainException(ErrorCode.LIMIT_EXCEEDED, "Value at row " + row.rowNumber() + ", column \""
                        + columns.get(i).name() + "\" is longer than " + limits.maxCellLength() + " characters.");
            }
            profiles.accept(i, value, row.kind(i));
        }
    }

    public long rowCount() {
        return rows;
    }

    public long blankRowsSkipped() {
        return blankRows;
    }

    public List<ColumnProfile> profiles() {
        return profiles.build();
    }
}
