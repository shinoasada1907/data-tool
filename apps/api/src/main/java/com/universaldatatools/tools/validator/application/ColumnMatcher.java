package com.universaldatatools.tools.validator.application;

import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.core.common.TextValues;
import com.universaldatatools.core.schema.DataSchema;
import com.universaldatatools.core.schema.SchemaField;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Pairs schema fields with source columns (tool-05 VD3): the column with the very same name first, for every field;
 * then, for fields still alone, the first free column whose name matches once trimmed and lower-cased. A column
 * serves one field at most.
 */
public final class ColumnMatcher {

    private ColumnMatcher() {
    }

    /**
     * @param columnIndexes per field, the index of its column, or -1
     * @param missingRequired a {@code FIELD_MISSING} item per required field without a column
     */
    public record Match(int[] columnIndexes, ValidatorRecords.Compatibility compatibility,
                        List<ProblemItem> missingRequired) {
    }

    public static Match match(DataSchema schema, List<String> columns) {
        List<SchemaField> fields = schema.fields();
        int[] indexes = new int[fields.size()];
        Arrays.fill(indexes, -1);
        boolean[] taken = new boolean[columns.size()];
        for (int f = 0; f < fields.size(); f++) {
            int column = columns.indexOf(fields.get(f).name());
            if (column >= 0 && !taken[column]) {
                indexes[f] = column;
                taken[column] = true;
            }
        }
        for (int f = 0; f < fields.size(); f++) {
            if (indexes[f] >= 0) {
                continue;
            }
            String wanted = loose(fields.get(f).name());
            for (int c = 0; c < columns.size(); c++) {
                if (!taken[c] && loose(columns.get(c)).equals(wanted)) {
                    indexes[f] = c;
                    taken[c] = true;
                    break;
                }
            }
        }
        List<ValidatorRecords.Matched> matched = new ArrayList<>();
        List<String> missingOptional = new ArrayList<>();
        List<ProblemItem> missingRequired = new ArrayList<>();
        for (int f = 0; f < fields.size(); f++) {
            SchemaField field = fields.get(f);
            if (indexes[f] >= 0) {
                matched.add(new ValidatorRecords.Matched(field.name(), columns.get(indexes[f])));
            } else if (field.required()) {
                missingRequired.add(new ProblemItem(field.name(), "FIELD_MISSING",
                        "No column matches this required field."));
            } else {
                missingOptional.add(field.name());
            }
        }
        List<String> extra = new ArrayList<>();
        for (int c = 0; c < columns.size(); c++) {
            if (!taken[c]) {
                extra.add(columns.get(c));
            }
        }
        return new Match(indexes, new ValidatorRecords.Compatibility(matched, missingOptional, extra), missingRequired);
    }

    private static String loose(String name) {
        String stripped = TextValues.strip(name);
        return stripped == null ? "" : stripped.toLowerCase(Locale.ROOT);
    }
}
