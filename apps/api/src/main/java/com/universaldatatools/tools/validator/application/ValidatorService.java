package com.universaldatatools.tools.validator.application;

import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.schema.Checked;
import com.universaldatatools.core.schema.DataSchema;
import com.universaldatatools.core.schema.SchemaDefinition;
import com.universaldatatools.core.schema.SchemaField;
import com.universaldatatools.core.schema.SchemaSpec;
import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.table.TableInfo;
import com.universaldatatools.core.validate.FieldRulePlan;
import com.universaldatatools.core.validate.FieldRuleRunner;
import com.universaldatatools.core.validate.FieldValidation;
import com.universaldatatools.core.validate.UniqueIndex;
import com.universaldatatools.core.validate.UniqueScope;
import com.universaldatatools.platform.dataset.DatasetSources;
import com.universaldatatools.platform.dataset.OpenedSource;
import com.universaldatatools.platform.dataset.SourceRef;
import com.universaldatatools.platform.run.RunRecord;
import com.universaldatatools.platform.run.RunSource;
import com.universaldatatools.platform.run.RunStore;
import com.universaldatatools.platform.run.RunWriter;
import com.universaldatatools.platform.run.SectionWriter;
import com.universaldatatools.tools.validator.application.ValidatorRecords.Config;
import com.universaldatatools.tools.validator.application.ValidatorRecords.ErrorRecord;
import com.universaldatatools.tools.validator.application.ValidatorRecords.RowRecord;
import com.universaldatatools.tools.validator.application.ValidatorRecords.Summary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Creates, finds and deletes validator runs (tool-05 VD2–VD4). A run is checked in this order: the schema, before
 * the dataset is even opened; the source; the columns; then every row, into the sections {@code valid} and
 * {@code invalid}.
 */
@Service
public class ValidatorService {

    public static final String TOOL = "validator";

    private final DatasetSources sources;
    private final RunStore runs;

    public ValidatorService(DatasetSources sources, RunStore runs) {
        this.sources = sources;
        this.runs = runs;
    }

    /** A run with its own JSON read back. */
    public record ValidatorRun(RunRecord record, Config config, Summary summary) {
    }

    /**
     * @throws DomainException {@code SCHEMA_INVALID} (items with pointers under {@code /schema}),
     *                         {@code SCHEMA_INCOMPATIBLE}, or the source's errors
     */
    public ValidatorRun create(SourceRef source, SchemaSpec spec) {
        DataSchema schema = switch (SchemaDefinition.check(spec, "/schema")) {
            case Checked.Ok<DataSchema> ok -> ok.value();
            case Checked.Invalid<DataSchema> invalid ->
                    throw new DomainException(ErrorCode.SCHEMA_INVALID, "The schema is not valid.", invalid.problems());
        };
        try (OpenedSource opened = sources.open(source)) {
            TableInfo info = opened.info();
            ColumnMatcher.Match match = ColumnMatcher.match(schema, info.columns().stream().map(Column::name).toList());
            if (!match.missingRequired().isEmpty()) {
                throw new DomainException(ErrorCode.SCHEMA_INCOMPATIBLE,
                        "Required fields of the schema have no matching column.", match.missingRequired());
            }
            RunSource runSource = new RunSource("source", opened.dataset().id(), opened.dataset().originalFileName(),
                    info.format(), info.options());
            return validate(opened, schema, match, runSource);
        }
    }

    public ValidatorRun get(UUID id) {
        return view(runs.get(TOOL, id));
    }

    public void delete(UUID id) {
        runs.delete(TOOL, id);
    }

    /**
     * The run with its JSON read back. PostgreSQL's {@code jsonb} keeps object keys in its own order, so the counts
     * are put back in theirs: codes sorted, fields in schema order.
     */
    ValidatorRun view(RunRecord record) {
        Config config = runs.json().treeToValue(record.config(), Config.class);
        Summary stored = runs.json().treeToValue(record.summary(), Summary.class);
        Map<String, Long> byField = new LinkedHashMap<>();
        for (SchemaField field : config.schema().fields()) {
            Long count = stored.errorCountsByField().get(field.name());
            if (count != null) {
                byField.put(field.name(), count);
            }
        }
        Summary summary = new Summary(stored.totalRows(), stored.validRows(), stored.invalidRows(),
                stored.errorCount(), new TreeMap<>(stored.errorCountsByCode()), byField);
        return new ValidatorRun(record, config, summary);
    }

    private ValidatorRun validate(OpenedSource opened, DataSchema schema, ColumnMatcher.Match match, RunSource source) {
        List<SchemaField> fields = schema.fields();
        List<FieldRulePlan> plans = fields.stream().map(field -> FieldRulePlan.of(field, Set.of())).toList();
        FieldRuleRunner runner = new FieldRuleRunner();
        UniqueIndex unique = new UniqueIndex(UniqueScope.ALL_ROWS);
        Counts counts = new Counts(fields.size());
        try (RunWriter writer = runs.begin(TOOL); Stream<Row> rows = opened.rows()) {
            SectionWriter valid = writer.section(ValidatorRecords.VALID);
            SectionWriter invalid = writer.section(ValidatorRecords.INVALID);
            Iterator<Row> iterator = rows.iterator();
            while (iterator.hasNext()) {
                Row row = iterator.next();
                int rowNumber = Math.toIntExact(row.rowNumber());
                List<String> values = new ArrayList<>(fields.size());
                List<ErrorRecord> errors = new ArrayList<>();
                unique.beginRow(rowNumber);
                for (int f = 0; f < fields.size(); f++) {
                    int column = match.columnIndexes()[f];
                    String value = column < 0 ? null : row.value(column);
                    values.add(value);
                    counts.length(f, value);
                    FieldValidation result = runner.validate(plans.get(f), value, rowNumber, unique);
                    if (result.failed()) {
                        errors.add(new ErrorRecord(fields.get(f).name(), result.failure().code().name(),
                                result.failure().rule(), result.failure().message()));
                        counts.error(f, result.failure().code().name());
                    }
                }
                unique.commitRow();
                counts.row(errors.isEmpty());
                (errors.isEmpty() ? valid : invalid).write(new RowRecord(row.rowNumber(), values, errors));
            }
            Config config = new Config(schema, match.compatibility(), Arrays.stream(counts.maxLengths).boxed().toList());
            return view(writer.commit(List.of(source), config, counts.summary(fields)));
        }
    }

    /** Tallies of one run. */
    private static final class Counts {

        private final int[] maxLengths;
        private final long[] byField;
        private final Map<String, Long> byCode = new TreeMap<>();
        private long valid;
        private long invalid;
        private long errors;

        Counts(int fields) {
            this.maxLengths = new int[fields];
            this.byField = new long[fields];
        }

        void length(int field, String value) {
            if (value != null) {
                maxLengths[field] = Math.max(maxLengths[field], value.codePointCount(0, value.length()));
            }
        }

        void error(int field, String code) {
            byField[field]++;
            byCode.merge(code, 1L, Long::sum);
            errors++;
        }

        void row(boolean isValid) {
            if (isValid) {
                valid++;
            } else {
                invalid++;
            }
        }

        Summary summary(List<SchemaField> fields) {
            Map<String, Long> perField = new LinkedHashMap<>();
            for (int f = 0; f < fields.size(); f++) {
                if (byField[f] > 0) {
                    perField.put(fields.get(f).name(), byField[f]);
                }
            }
            return new Summary(valid + invalid, valid, invalid, errors, byCode, perField);
        }
    }
}
