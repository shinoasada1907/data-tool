package com.universalimporter.domain.config;

import com.universalimporter.domain.common.ProblemItem;
import com.universalimporter.domain.mapping.FieldMapping;
import com.universalimporter.domain.mapping.MappingConfig;
import com.universalimporter.domain.mapping.MappingType;
import com.universalimporter.domain.schema.FieldSpec;
import com.universalimporter.domain.schema.TargetSchema;
import com.universalimporter.domain.transformation.TransformationConfig;
import com.universalimporter.domain.transformation.TransformationStep;
import com.universalimporter.domain.validation.ValidationConfig;
import com.universalimporter.domain.validation.ValidationRuleConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ImportConfigurationTest {

    private static final UUID ID = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");

    @Test
    void empty_has_no_schema_fields_and_no_version() {
        ImportConfiguration empty = ImportConfiguration.empty(ID);

        assertThat(empty.sessionId()).isEqualTo(ID);
        assertThat(empty.schema().isEmpty()).isTrue();
        assertThat(empty.version()).isNull();
    }

    @Test
    void empty_has_no_mappings() {
        assertThat(ImportConfiguration.empty(ID).mapping()).isEqualTo(MappingConfig.empty());
    }

    @Test
    void with_mapping_warns_about_each_unmapped_field_in_schema_order() {
        ImportConfiguration configuration = configuration(schema(new FieldSpec("name", "string", true, 0),
                new FieldSpec("note", "string", false, 1), new FieldSpec("age", "number", false, 2)), MappingConfig.empty());

        ConfigChange change = configuration.withMapping(new MappingConfig(List.of(constant("name"))));

        assertThat(change.configuration().mapping().mappings()).containsExactly(constant("name"));
        assertThat(change.warnings()).containsExactly(
                new ProblemItem("note", "TARGET_FIELD_UNMAPPED", "Field is not mapped."),
                new ProblemItem("age", "TARGET_FIELD_UNMAPPED", "Field is not mapped."));
    }

    @Test
    void with_schema_prunes_the_mapping_of_removed_fields() {
        ImportConfiguration configuration = configuration(
                schema(new FieldSpec("name", "string", true, 0), new FieldSpec("phone", "string", false, 1)),
                new MappingConfig(List.of(constant("name"), constant("phone"))));

        ConfigChange change = configuration.withSchema(schema(new FieldSpec("name", "string", true, 0)));

        assertThat(change.configuration().mapping().mappings()).containsExactly(constant("name"));
        assertThat(change.warnings()).containsExactly(new ProblemItem("phone", "CONFIG_PRUNED",
                "Mapping for this field was removed because the field no longer exists."));
    }

    @Test
    void changing_a_field_type_keeps_its_mapping() {
        ImportConfiguration configuration = configuration(schema(new FieldSpec("name", "string", true, 0)),
                new MappingConfig(List.of(constant("name"))));

        ConfigChange change = configuration.withSchema(schema(new FieldSpec("name", "number", true, 0)));

        assertThat(change.configuration().mapping().mappings()).containsExactly(constant("name"));
        assertThat(change.warnings()).isEmpty();
    }

    @Test
    void reordering_the_schema_reorders_the_mapping() {
        ImportConfiguration configuration = configuration(
                schema(new FieldSpec("name", "string", true, 0), new FieldSpec("country", "string", false, 1)),
                new MappingConfig(List.of(constant("name"), constant("country"))));

        ConfigChange change = configuration.withSchema(
                schema(new FieldSpec("country", "string", false, 0), new FieldSpec("name", "string", true, 1)));

        assertThat(change.configuration().mapping().mappings()).containsExactly(constant("country"), constant("name"));
    }

    @Test
    void the_mapping_is_always_kept_in_schema_order() {
        ImportConfiguration configuration = configuration(
                schema(new FieldSpec("name", "string", true, 0), new FieldSpec("country", "string", false, 1)),
                new MappingConfig(List.of(constant("country"), constant("name"))));

        assertThat(configuration.mapping().mappings()).containsExactly(constant("name"), constant("country"));
    }

    @Test
    void with_schema_prunes_transformations_after_mappings() {
        ImportConfiguration configuration = new ImportConfiguration(ID,
                schema(new FieldSpec("name", "string", true, 0), new FieldSpec("phone", "string", false, 1)),
                new MappingConfig(List.of(constant("phone"))),
                new TransformationConfig(List.of(trim("name", 0), trim("phone", 0))), ValidationConfig.empty(), 3L);

        ConfigChange change = configuration.withSchema(schema(new FieldSpec("name", "string", true, 0)));

        assertThat(change.configuration().transformations().transformations()).containsExactly(trim("name", 0));
        assertThat(change.warnings()).extracting(ProblemItem::message).containsExactly(
                "Mapping for this field was removed because the field no longer exists.",
                "Transformations removed because field 'phone' no longer exists.");
    }

    @Test
    void with_transformations_replaces_them_without_warnings() {
        ImportConfiguration configuration = configuration(schema(new FieldSpec("name", "string", true, 0)),
                MappingConfig.empty());

        ConfigChange change = configuration.withTransformations(new TransformationConfig(List.of(trim("name", 0))));

        assertThat(change.configuration().transformations().transformations()).containsExactly(trim("name", 0));
        assertThat(change.warnings()).isEmpty();
    }

    @Test
    void transformations_are_always_kept_in_schema_then_step_order() {
        ImportConfiguration configuration = new ImportConfiguration(ID,
                schema(new FieldSpec("name", "string", true, 0), new FieldSpec("note", "string", false, 1)),
                MappingConfig.empty(),
                new TransformationConfig(List.of(trim("note", 0), trim("name", 1), trim("name", 0))),
                ValidationConfig.empty(), null);

        assertThat(configuration.transformations().transformations())
                .containsExactly(trim("name", 0), trim("name", 1), trim("note", 0));
    }

    @Test
    void with_schema_prunes_validations_last() {
        ImportConfiguration configuration = new ImportConfiguration(ID,
                schema(new FieldSpec("name", "string", true, 0), new FieldSpec("phone", "string", false, 1)),
                MappingConfig.empty(), new TransformationConfig(List.of(trim("phone", 0))),
                new ValidationConfig(List.of(rule("phone", "unique"), rule("name", "email"))), 3L);

        ConfigChange change = configuration.withSchema(schema(new FieldSpec("name", "number", true, 0)));

        assertThat(change.configuration().validations().validations()).isEmpty();
        assertThat(change.warnings()).extracting(ProblemItem::message).containsExactly(
                "Transformations removed because field 'phone' no longer exists.",
                "Validation rules removed because field 'phone' no longer exists.",
                "Rule 'email' removed because field 'name' is no longer of type string.");
    }

    @Test
    void with_validations_replaces_them_and_passes_the_warnings_on() {
        ImportConfiguration configuration = configuration(schema(new FieldSpec("name", "string", true, 0)),
                MappingConfig.empty());
        List<ProblemItem> warnings = List.of(new ProblemItem("name", "RULE_IMPLIED_BY_SCHEMA", "ignored"));

        ConfigChange change = configuration.withValidations(new ValidationConfig(List.of(rule("name", "unique"))), warnings);

        assertThat(change.configuration().validations().validations()).containsExactly(rule("name", "unique"));
        assertThat(change.warnings()).isEqualTo(warnings);
    }

    @Test
    void validations_are_always_kept_in_schema_then_rule_order() {
        ImportConfiguration configuration = new ImportConfiguration(ID,
                schema(new FieldSpec("name", "string", true, 0), new FieldSpec("note", "string", false, 1)),
                MappingConfig.empty(), TransformationConfig.empty(),
                new ValidationConfig(List.of(rule("note", "unique"), rule("name", "unique"), rule("name", "email"))),
                null);

        assertThat(configuration.validations().validations())
                .containsExactly(rule("name", "email"), rule("name", "unique"), rule("note", "unique"));
    }

    @Test
    void with_schema_replaces_the_schema_and_keeps_the_version() {
        TargetSchema schema = TargetSchema.define(List.of(new FieldSpec("email", "email", true, 0)));

        ConfigChange change = ImportConfiguration.empty(ID).withSchema(schema);

        assertThat(change.configuration().schema()).isEqualTo(schema);
        assertThat(change.configuration().sessionId()).isEqualTo(ID);
        assertThat(change.configuration().version()).isNull();
        assertThat(change.warnings()).isEmpty();
    }

    private static TargetSchema schema(FieldSpec... fields) {
        return TargetSchema.define(List.of(fields));
    }

    private static ImportConfiguration configuration(TargetSchema schema, MappingConfig mapping) {
        return new ImportConfiguration(ID, schema, mapping, TransformationConfig.empty(), ValidationConfig.empty(), 3L);
    }

    private static ValidationRuleConfig rule(String field, String type) {
        return new ValidationRuleConfig(field, type, null);
    }

    private static TransformationStep trim(String field, int order) {
        return new TransformationStep(field, order, "trim", null);
    }

    private static FieldMapping constant(String field) {
        return new FieldMapping(field, MappingType.CONSTANT, null, "x");
    }
}
