package com.universaldatatools.tools.validator.application;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.platform.run.RunStore;
import com.universaldatatools.tools.validator.application.ValidatorRecords.RowRecord;
import com.universaldatatools.tools.validator.application.ValidatorService.ValidatorRun;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Pages of a validator run's rows (tool-05 VD5). Without a filter the total comes from the summary and skipped
 * lines are not parsed; with one the whole section is scanned to count.
 */
@Service
public class ValidatorRows {

    public static final int MAX_SIZE = 200;

    public enum View { VALID, INVALID }

    private final ValidatorService service;
    private final RunStore runs;

    public ValidatorRows(ValidatorService service, RunStore runs) {
        this.service = service;
        this.runs = runs;
    }

    /** @param rows at most {@code size}; empty past the last page */
    public record Page(View view, int number, int size, long totalElements, List<RowRecord> rows, List<String> fields) {

        public long totalPages() {
            return (totalElements + size - 1) / size;
        }
    }

    /**
     * @param field only rows with an error on this field ({@code INVALID} only)
     * @param code  only rows with an error of this code ({@code INVALID} only)
     * @throws DomainException {@code REQUEST_INVALID} for a wrong view, page or size; {@code RUN_NOT_FOUND}
     */
    public Page page(UUID id, String view, String field, String code, int page, int size) {
        View parsed = parseView(view);
        if (page < 0) {
            throw new DomainException(ErrorCode.REQUEST_INVALID, "page must be 0 or more.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new DomainException(ErrorCode.REQUEST_INVALID, "size must be between 1 and " + MAX_SIZE + ".");
        }
        ValidatorRun run = service.get(id);
        List<String> fields = run.config().schema().fields().stream().map(f -> f.name()).toList();
        String section = parsed == View.VALID ? ValidatorRecords.VALID : ValidatorRecords.INVALID;
        long skip = (long) page * size;
        boolean filtered = parsed == View.INVALID && (blankToNull(field) != null || blankToNull(code) != null);
        if (!filtered) {
            long total = parsed == View.VALID ? run.summary().validRows() : run.summary().invalidRows();
            try (Stream<RowRecord> rows = runs.read(id, section, RowRecord.class, skip)) {
                return new Page(parsed, page, size, total, rows.limit(size).toList(), fields);
            }
        }
        String wantedField = blankToNull(field);
        String wantedCode = blankToNull(code);
        List<RowRecord> slice = new ArrayList<>();
        long total = 0;
        try (Stream<RowRecord> rows = runs.read(id, section, RowRecord.class, 0)) {
            Iterator<RowRecord> iterator = rows.iterator();
            while (iterator.hasNext()) {
                RowRecord row = iterator.next();
                boolean matches = row.errors().stream().anyMatch(error ->
                        (wantedField == null || wantedField.equals(error.field()))
                                && (wantedCode == null || wantedCode.equals(error.code())));
                if (matches) {
                    if (total >= skip && slice.size() < size) {
                        slice.add(row);
                    }
                    total++;
                }
            }
        }
        return new Page(parsed, page, size, total, slice, fields);
    }

    private static View parseView(String view) {
        if (view == null || view.isBlank()) {
            return View.VALID;
        }
        try {
            return View.valueOf(view.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException(ErrorCode.REQUEST_INVALID, "view must be VALID or INVALID.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
