package com.universaldatatools.tools.importer.domain.pipeline;

import com.universaldatatools.tools.importer.domain.transformation.TransformationConfig;
import com.universaldatatools.tools.importer.domain.validation.ValidationConfig;
import com.universaldatatools.tools.importer.domain.validation.ValidationRuleConfig;
import com.universaldatatools.tools.importer.domain.importsession.SourceSchema;
import com.universaldatatools.tools.importer.domain.mapping.MappingConfig;
import com.universaldatatools.tools.importer.domain.mapping.MappingSpec;
import com.universaldatatools.tools.importer.domain.schema.FieldSpec;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;
import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.transform.TransformationStep;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** The sample data set of the import-pipeline spec, built in memory; empty cells are null. */
public final class SampleDataset {

    public static final SourceSchema SOURCE = new SourceSchema(List.of(new Column(0, "Họ tên"),
            new Column(1, "Email"), new Column(2, "Tuổi"), new Column(3, "Ngày sinh")), 6, null);

    public static final TargetSchema SCHEMA = TargetSchema.define(List.of(
            new FieldSpec("name", "string", true, 0),
            new FieldSpec("email", "email", true, 1),
            new FieldSpec("age", "number", false, 2),
            new FieldSpec("dob", "date", false, 3)));

    public static final MappingConfig MAPPING = MappingConfig.define(List.of(
            new MappingSpec("name", "SOURCE_COLUMN", "Họ tên", null),
            new MappingSpec("email", "SOURCE_COLUMN", "Email", null),
            new MappingSpec("age", "SOURCE_COLUMN", "Tuổi", null),
            new MappingSpec("dob", "SOURCE_COLUMN", "Ngày sinh", null)), SCHEMA, SOURCE);

    public static final TransformationConfig TRANSFORMATIONS = new TransformationConfig(List.of(
            new TransformationStep("name", 0, "trim", null),
            new TransformationStep("email", 0, "trim", null),
            new TransformationStep("email", 1, "lowercase", null),
            new TransformationStep("dob", 0, "dateFormat", Map.of("inputFormat", "dd/MM/yyyy"))));

    public static final ValidationConfig VALIDATIONS =
            new ValidationConfig(List.of(new ValidationRuleConfig("email", "unique", null)));

    public static final PipelineConfig CONFIG = new PipelineConfig(SOURCE, SCHEMA, MAPPING, TRANSFORMATIONS, VALIDATIONS);

    private SampleDataset() {
    }

    public static List<Row> rows() {
        return List.of(
                row(2, "  An ", "AN@X.COM", "30", "25/12/1990"),
                row(3, "Bình", "binh@x", "abc", "31/02/1990"),
                row(4, null, "an@x.com", null, null),
                row(5, "Cường", "cuong@x.com", null, null),
                row(6, "Dũng", "dung@x.com", "abc", null),
                row(7, "Dũng 2", "dung@x.com", "40", null));
    }

    private static Row row(long number, String... values) {
        return new Row(number, Arrays.asList(values));
    }
}
