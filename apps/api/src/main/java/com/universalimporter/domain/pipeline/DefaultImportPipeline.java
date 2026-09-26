package com.universalimporter.domain.pipeline;

import com.universalimporter.domain.common.RowErrorCode;
import com.universalimporter.domain.common.ThrottledWarnings;
import com.universalimporter.domain.mapping.MappingStrategies;
import com.universalimporter.domain.mapping.RowMapper;
import com.universalimporter.domain.schema.TargetField;
import com.universalimporter.domain.source.ImportRow;
import com.universalimporter.domain.transformation.FieldTransformResult;
import com.universalimporter.domain.transformation.TransformationEngine;
import com.universalimporter.domain.transformation.TransformationStep;
import com.universalimporter.domain.validation.FieldValidation;
import com.universalimporter.domain.validation.FieldValidator;
import com.universalimporter.domain.validation.UniqueTracker;
import com.universalimporter.domain.validation.ValidationRuleConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The pipeline of V0.1 (design P1–P4). Streams: only counters and the unique values of one run stay in memory.
 * Deterministic: the same rows and config give the same results, whatever ran before, since each run has its own
 * {@link UniqueTracker}. A failure in one field of one row, even a bug, becomes that field's error; the run goes on.
 */
public final class DefaultImportPipeline implements ImportPipeline {

    static final String MAPPING_FAILED = "Unexpected error while mapping the value.";

    private final MappingStrategies strategies;
    private final TransformationEngine engine;
    private final FieldValidator validator;
    private final ThrottledWarnings warnings =
            new ThrottledWarnings(System.getLogger(DefaultImportPipeline.class.getName()));

    public DefaultImportPipeline(MappingStrategies strategies, TransformationEngine engine, FieldValidator validator) {
        this.strategies = strategies;
        this.engine = engine;
        this.validator = validator;
    }

    @Override
    public PipelineSummary execute(Stream<ImportRow> rows, PipelineConfig config, RowResultSink sink) {
        Run run = new Run(config);
        rows.forEachOrdered(row -> sink.accept(run.process(row)));
        return run.summary();
    }

    /** The state of one execution; resolved once, so rows are never looked up by name. */
    private final class Run {

        private final List<TargetField> fields;
        private final RowMapper mapper;
        private final List<List<TransformationStep>> steps = new ArrayList<>();
        private final List<List<ValidationRuleConfig>> rules = new ArrayList<>();
        private final UniqueTracker tracker = new UniqueTracker();
        private final Map<String, Long> byCode = new TreeMap<>();
        private final long[] byField;
        private long valid;
        private long invalid;

        Run(PipelineConfig config) {
            this.fields = config.schema().fields();
            this.mapper = RowMapper.of(config.schema(), config.mapping(), config.source(), strategies);
            for (TargetField field : fields) {
                steps.add(config.transformations().stepsFor(field.name()));
                rules.add(config.validations().rulesFor(field.name()));
            }
            this.byField = new long[fields.size()];
        }

        RowResult process(ImportRow row) {
            int rowNumber = Math.toIntExact(row.rowNumber());
            tracker.beginRow(rowNumber);
            Map<String, Object> converted = new LinkedHashMap<>();
            Map<String, Object> transformed = new LinkedHashMap<>();
            List<ImportError> errors = new ArrayList<>();
            for (int i = 0; i < fields.size(); i++) {
                TargetField field = fields.get(i);
                String raw;
                try {
                    raw = mapper.mapField(row, i);
                } catch (RuntimeException bug) {
                    warnings.warn("mapping", () -> "Mapping failed unexpectedly on field " + field.name() + ": "
                            + bug.getClass().getName());
                    errors.add(new ImportError(rowNumber, field.name(), ErrorStage.TRANSFORMATION, "mapping", null,
                            RowErrorCode.TRANSFORMATION_FAILED, MAPPING_FAILED, null));
                    transformed.put(field.name(), null);
                    converted.put(field.name(), null);
                    continue;
                }
                FieldTransformResult t = engine.apply(field.name(), field.type(), raw, steps.get(i));
                if (t.failed()) {
                    errors.add(new ImportError(rowNumber, field.name(), ErrorStage.TRANSFORMATION, t.error().rule(),
                            t.error().step(), RowErrorCode.TRANSFORMATION_FAILED, t.error().message(), raw));
                    transformed.put(field.name(), null);
                    converted.put(field.name(), null);
                    continue;
                }
                transformed.put(field.name(), t.value());
                FieldValidation v = validator.validate(field, rules.get(i), t.value(), rowNumber, tracker);
                if (v.failed()) {
                    errors.add(new ImportError(rowNumber, field.name(), ErrorStage.VALIDATION, v.failure().rule(), null,
                            v.failure().code(), v.failure().message(), raw));
                }
                converted.put(field.name(), v.value());
            }
            return finish(rowNumber, converted, transformed, errors);
        }

        private RowResult finish(int rowNumber, Map<String, Object> converted, Map<String, Object> transformed,
                                 List<ImportError> errors) {
            if (errors.isEmpty()) {
                tracker.commitRow();
                valid++;
                return new RowResult(rowNumber, true, converted, errors);
            }
            // Any error, even in another field, and the row's unique values must not block a later valid row.
            tracker.discardRow();
            invalid++;
            for (ImportError error : errors) {
                byCode.merge(error.code().name(), 1L, Long::sum);
                byField[indexOf(error.fieldName())]++;
            }
            return new RowResult(rowNumber, false, transformed, errors);
        }

        private int indexOf(String fieldName) {
            for (int i = 0; i < fields.size(); i++) {
                if (fields.get(i).name().equals(fieldName)) {
                    return i;
                }
            }
            throw new IllegalStateException("Error for a field outside the schema");
        }

        PipelineSummary summary() {
            Map<String, Long> fieldCounts = new LinkedHashMap<>();
            for (int i = 0; i < fields.size(); i++) {
                if (byField[i] > 0) {
                    fieldCounts.put(fields.get(i).name(), byField[i]);
                }
            }
            return new PipelineSummary(valid + invalid, valid, invalid, byCode, fieldCounts);
        }
    }
}
