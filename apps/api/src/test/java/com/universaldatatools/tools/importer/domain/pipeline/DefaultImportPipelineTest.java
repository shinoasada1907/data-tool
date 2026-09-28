package com.universaldatatools.tools.importer.domain.pipeline;

import com.universaldatatools.tools.importer.domain.transformation.TransformationConfig;
import com.universaldatatools.tools.importer.domain.validation.FieldValidator;
import com.universaldatatools.core.common.RowErrorCode;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.tools.importer.domain.mapping.MappingStrategies;
import com.universaldatatools.tools.importer.domain.mapping.MappingStrategy;
import com.universaldatatools.tools.importer.domain.mapping.MappingType;
import com.universaldatatools.tools.importer.domain.mapping.ResolvedMapping;
import com.universaldatatools.tools.importer.domain.mapping.ConstantMappingStrategy;
import com.universaldatatools.core.transform.DateFormatTransformation;
import com.universaldatatools.core.transform.DefaultValueTransformation;
import com.universaldatatools.core.transform.LowercaseTransformation;
import com.universaldatatools.core.transform.Transformation;
import com.universaldatatools.core.transform.TransformationContext;
import com.universaldatatools.core.transform.TransformationEngine;
import com.universaldatatools.core.transform.TransformationRegistry;
import com.universaldatatools.core.transform.TransformationStep;
import com.universaldatatools.core.transform.TrimTransformation;
import com.universaldatatools.core.transform.UppercaseTransformation;
import com.universaldatatools.core.validate.ValidationRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultImportPipelineTest {

    private final DefaultImportPipeline pipeline = new DefaultImportPipeline(MappingStrategies.standard(),
            new TransformationEngine(TransformationRegistry.standard()), new FieldValidator(ValidationRegistry.standard()));

    private final List<RowResult> rows = new ArrayList<>();

    @Test
    void every_row_reaches_the_sink_in_file_order() {
        run();

        assertThat(rows).extracting(RowResult::rowNumber).containsExactly(2, 3, 4, 5, 6, 7);
        assertThat(rows).extracting(RowResult::valid).containsExactly(true, false, false, true, false, true);
    }

    @Test
    void a_valid_row_holds_converted_values_in_schema_order() {
        run();

        RowResult row2 = rows.get(0);
        assertThat(row2.values().keySet()).containsExactly("name", "email", "age", "dob");
        assertThat(row2.values().values())
                .containsExactly("An", "an@x.com", new BigDecimal("30"), LocalDate.of(1990, 12, 25));
        assertThat(row2.errors()).isEmpty();
        assertThat(rows.get(3).values().values()).containsExactly("Cường", "cuong@x.com", null, null);
    }

    @Test
    void an_invalid_row_holds_transformed_strings_and_one_error_per_failing_field() {
        run();

        RowResult row3 = rows.get(1);
        assertThat(row3.values().values()).containsExactly("Bình", "binh@x", "abc", null);
        assertThat(row3.errors()).containsExactly(
                new ImportError(3, "email", ErrorStage.VALIDATION, "email", null, RowErrorCode.VALIDATION_EMAIL,
                        "Value is not a valid email address.", "binh@x"),
                new ImportError(3, "age", ErrorStage.VALIDATION, "type", null, RowErrorCode.VALIDATION_TYPE,
                        "Value is not a valid number.", "abc"),
                new ImportError(3, "dob", ErrorStage.TRANSFORMATION, "dateFormat", 0, RowErrorCode.TRANSFORMATION_FAILED,
                        "Value does not match pattern dd/MM/yyyy", "31/02/1990"));
    }

    @Test
    void required_and_unique_errors_on_one_row() {
        run();

        assertThat(rows.get(2).errors()).containsExactly(
                new ImportError(4, "name", ErrorStage.VALIDATION, "required", null, RowErrorCode.VALIDATION_REQUIRED,
                        "Value is required.", null),
                new ImportError(4, "email", ErrorStage.VALIDATION, "unique", null, RowErrorCode.VALIDATION_UNIQUE,
                        "Duplicate value; first seen in row 2.", "an@x.com"));
    }

    @Test
    void an_invalid_row_does_not_reserve_its_unique_values() {
        run();

        assertThat(rows.get(5).valid()).isTrue();
        assertThat(rows.get(5).values().get("email")).isEqualTo("dung@x.com");
    }

    @Test
    void the_summary_counts_errors_by_code_and_by_field() {
        PipelineSummary summary = run();

        assertThat(summary.total()).isEqualTo(6);
        assertThat(summary.valid()).isEqualTo(3);
        assertThat(summary.invalid()).isEqualTo(3);
        assertThat(summary.errorCountsByCode()).containsExactly(Map.entry("TRANSFORMATION_FAILED", 1L),
                Map.entry("VALIDATION_EMAIL", 1L), Map.entry("VALIDATION_REQUIRED", 1L), Map.entry("VALIDATION_TYPE", 2L),
                Map.entry("VALIDATION_UNIQUE", 1L));
        assertThat(summary.errorCountsByField()).containsExactly(Map.entry("name", 1L), Map.entry("email", 2L),
                Map.entry("age", 2L), Map.entry("dob", 1L));
    }

    @Test
    void each_run_starts_with_no_unique_values() {
        PipelineSummary first = run();
        List<RowResult> firstRows = List.copyOf(rows);
        rows.clear();

        PipelineSummary second = run();

        assertThat(second).isEqualTo(first);
        assertThat(rows).isEqualTo(firstRows);
        assertThat(rows.get(0).valid()).isTrue();
    }

    @Test
    void a_bug_in_a_transformation_only_affects_its_row() {
        DefaultImportPipeline withBug = new DefaultImportPipeline(MappingStrategies.standard(),
                new TransformationEngine(new TransformationRegistry(List.of(new TrimTransformation(),
                        new UppercaseTransformation(), new LowercaseTransformation(), new DefaultValueTransformation(),
                        new DateFormatTransformation(), new ExplodeOnBoom()))),
                new FieldValidator(ValidationRegistry.standard()));
        PipelineConfig config = new PipelineConfig(SampleDataset.SOURCE, SampleDataset.SCHEMA, SampleDataset.MAPPING,
                new TransformationConfig(List.of(new TransformationStep("name", 0, "explode", null))),
                SampleDataset.VALIDATIONS);
        List<Row> five = List.of(row(2, "A", "a@x.com"), row(3, "boom", "b@x.com"), row(4, "C", "c@x.com"),
                row(5, "D", "d@x.com"), row(6, "E", "e@x.com"));

        PipelineSummary summary = withBug.execute(five.stream(), config, rows::add);

        assertThat(summary.total()).isEqualTo(5);
        assertThat(summary.invalid()).isEqualTo(1);
        assertThat(rows.get(1).errors()).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo(RowErrorCode.TRANSFORMATION_FAILED);
            assertThat(error.message()).isEqualTo("Unexpected error while applying transformation.");
            assertThat(error.sourceValue()).isEqualTo("boom");
        });
        assertThat(rows).filteredOn(RowResult::valid).hasSize(4);
    }

    @Test
    void a_bug_in_a_mapping_strategy_becomes_a_mapping_error() {
        MappingStrategy failsOnRow2 = new MappingStrategy() {
            @Override
            public MappingType type() {
                return MappingType.SOURCE_COLUMN;
            }

            @Override
            public String map(Row row, ResolvedMapping mapping) {
                if (row.rowNumber() == 2) {
                    throw new IllegalStateException("mapping bug");
                }
                return row.value(mapping.columnIndex());
            }
        };
        DefaultImportPipeline withBug = new DefaultImportPipeline(
                new MappingStrategies(List.of(failsOnRow2, new ConstantMappingStrategy())),
                new TransformationEngine(TransformationRegistry.standard()), new FieldValidator(ValidationRegistry.standard()));

        withBug.execute(Stream.of(row(2, "A", "a@x.com"), row(3, "B", "b@x.com")), SampleDataset.CONFIG, rows::add);

        assertThat(rows.get(0).errors()).first().isEqualTo(new ImportError(2, "name", ErrorStage.TRANSFORMATION, "mapping",
                null, RowErrorCode.TRANSFORMATION_FAILED, "Unexpected error while mapping the value.", null));
        assertThat(rows.get(1).valid()).isTrue();
    }

    private PipelineSummary run() {
        return pipeline.execute(SampleDataset.rows().stream(), SampleDataset.CONFIG, rows::add);
    }

    private static Row row(long number, String... values) {
        return new Row(number, Arrays.asList(values));
    }

    /** A transformation with a bug that shows only on the value "boom". */
    static final class ExplodeOnBoom implements Transformation {

        @Override
        public String type() {
            return "explode";
        }

        @Override
        public List<String> validate(TransformationContext context) {
            return List.of();
        }

        @Override
        public String transform(String value, TransformationContext context) {
            if ("boom".equals(value)) {
                throw new IllegalStateException("boom");
            }
            return value;
        }
    }
}
