package com.universaldatatools.tools.importer.domain.schema;

import com.universaldatatools.core.schema.FieldType;
import com.universaldatatools.core.common.DomainException;
import com.universaldatatools.core.common.ErrorCode;
import com.universaldatatools.core.common.ProblemItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class TargetSchemaTest {

    @Test
    void define_trims_names_sorts_by_order_and_renumbers_from_zero() {
        TargetSchema schema = TargetSchema.define(List.of(f(" email ", "email", true, 5), f("name", "string", false, 1)));

        assertThat(schema.fields()).containsExactly(
                new TargetField("name", FieldType.STRING, false, 0),
                new TargetField("email", FieldType.EMAIL, true, 1));
    }

    @Test
    void an_empty_list_is_invalid() {
        assertThat(errorsOf(List.of()))
                .containsExactly(item(null, "Schema must contain at least one field."));
    }

    @Test
    void a_blank_name_is_invalid() {
        assertThat(errorsOf(List.of(f("   ", "string", false, 0))))
                .containsExactly(item(null, "Field name must not be blank."));
    }

    @Test
    void a_missing_name_is_invalid() {
        assertThat(errorsOf(List.of(f(null, "string", false, 0))))
                .containsExactly(item(null, "Field name must not be blank."));
    }

    @Test
    void a_name_over_100_characters_is_invalid() {
        String name = "a".repeat(101);

        assertThat(errorsOf(List.of(f(name, "string", false, 0))))
                .containsExactly(item(name, "Field name must be at most 100 characters."));
    }

    @Test
    void a_name_of_exactly_100_characters_is_valid() {
        TargetSchema schema = TargetSchema.define(List.of(f("a".repeat(100), "string", false, 0)));

        assertThat(schema.fields()).hasSize(1);
    }

    @Test
    void names_that_differ_only_in_case_are_duplicates() {
        assertThat(errorsOf(List.of(f("Email", "string", false, 0), f("email", "string", false, 1))))
                .containsExactly(item("email", "Duplicate field name."));
    }

    @Test
    void an_unknown_type_is_invalid() {
        assertThat(errorsOf(List.of(f("note", "text", false, 0))))
                .containsExactly(item("note", "Unknown field type."));
    }

    @Test
    void a_missing_order_is_invalid() {
        assertThat(errorsOf(List.of(f("a", "string", false, null))))
                .containsExactly(item("a", "Field order is required."));
    }

    @Test
    void a_repeated_order_is_invalid() {
        assertThat(errorsOf(List.of(f("a", "string", false, 1), f("b", "string", false, 1))))
                .containsExactly(item("b", "Duplicate field order."));
    }

    @Test
    void every_problem_is_reported_in_input_order() {
        List<FieldSpec> specs = List.of(f("", "string", false, 0), f("age", "int", false, 1), f("AGE", "number", false, 2));

        assertThat(errorsOf(specs)).containsExactly(
                item(null, "Field name must not be blank."),
                item("age", "Unknown field type."),
                item("AGE", "Duplicate field name."));
    }

    @Test
    void the_error_is_schema_invalid_with_a_summary_message() {
        DomainException ex = catchThrowableOfType(DomainException.class, () -> TargetSchema.define(List.of()));

        assertThat(ex.code()).isEqualTo(ErrorCode.SCHEMA_INVALID);
        assertThat(ex.getMessage()).isEqualTo("Target schema is invalid.");
    }

    @Test
    void field_names_and_lookup_use_the_exact_name() {
        TargetSchema schema = TargetSchema.define(List.of(f("name", "string", false, 0), f("email", "email", true, 1)));

        assertThat(schema.fieldNames()).containsExactlyInAnyOrder("name", "email");
        assertThat(schema.field("email")).contains(new TargetField("email", FieldType.EMAIL, true, 1));
        assertThat(schema.field("Email")).isEmpty();
        assertThat(schema.isEmpty()).isFalse();
    }

    @Test
    void the_empty_schema_has_no_fields() {
        assertThat(TargetSchema.empty().isEmpty()).isTrue();
        assertThat(TargetSchema.empty().fields()).isEmpty();
    }

    private static FieldSpec f(String name, String type, boolean required, Integer order) {
        return new FieldSpec(name, type, required, order);
    }

    private static ProblemItem item(String field, String message) {
        return new ProblemItem(field, "SCHEMA_INVALID", message);
    }

    private static List<ProblemItem> errorsOf(List<FieldSpec> specs) {
        DomainException ex = catchThrowableOfType(DomainException.class, () -> TargetSchema.define(specs));
        assertThat(ex).as("define(%s) should fail", specs).isNotNull();
        assertThat(ex.code()).isEqualTo(ErrorCode.SCHEMA_INVALID);
        return ex.items();
    }
}
