package com.universaldatatools.core.schema;

import com.universaldatatools.core.common.ProblemItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** core-03 task 2: every problem of a definition, each pointing at the wrong value. */
class SchemaDefinitionTest {

    @Test
    void a_minimal_schema() {
        DataSchema schema = ok(schema("KH", f("email", "email")));
        assertThat(schema.fields()).containsExactly(
                new SchemaField("email", FieldType.EMAIL, false, FieldConstraints.NONE));
    }

    @Test
    void names_are_trimmed() {
        assertThat(ok(schema("KH", f(" Email ", "string"))).fields().get(0).name()).isEqualTo("Email");
    }

    @Test
    void schema_name_and_field_count() {
        assertProblem(schema("  ", f("a", "string")), "SCHEMA_NAME_INVALID", "/name");
        assertProblem(schema("x".repeat(201), f("a", "string")), "SCHEMA_NAME_INVALID", "/name");
        assertProblem(new SchemaSpec("KH", List.of()), "SCHEMA_FIELDS_INVALID", "/fields");
        assertProblem(new SchemaSpec("KH", Collections.nCopies(501, f("a", "string"))), "SCHEMA_FIELDS_INVALID", "/fields");
    }

    @Test
    void field_names() {
        ProblemItem blank = only(schema("KH", f(" ", "string")));
        assertThat(blank).isEqualTo(new ProblemItem(null, "FIELD_NAME_INVALID", "Field name must not be blank.", "/fields/0/name"));
        assertThat(only(schema("KH", f("n".repeat(101), "string"))).message())
                .isEqualTo("Field name must be at most 100 characters.");
    }

    @Test
    void every_problem_in_order() {
        assertThat(problems(schema("", f("Email", "email"), f("email", "text")))).containsExactly(
                new ProblemItem(null, "SCHEMA_NAME_INVALID", "Schema name must be 1-200 characters.", "/name"),
                new ProblemItem("email", "FIELD_NAME_DUPLICATE", "Duplicate field name.", "/fields/1/name"),
                new ProblemItem("email", "FIELD_TYPE_INVALID", "Unknown field type.", "/fields/1/type"));
    }

    @Test
    void constraints_that_do_not_fit_the_type_or_each_other() {
        assertConstraint(field("age", "string", c().min(18)), "min", "'min' is only allowed on number fields.");
        assertConstraint(field("age", "number", c().min(65).max(18)), "max", "'max' must not be less than 'min'.");
        assertConstraint(field("n", "number", c().maxLength(5)), "maxLength",
                "'maxLength' is only allowed on string and email fields.");
        assertConstraint(field("s", "string", c().maxLength(40000)), "maxLength", "'maxLength' must be between 0 and 32767.");
        assertConstraint(field("s", "string", c().minLength(5).maxLength(2)), "maxLength",
                "'maxLength' must not be less than 'minLength'.");
        assertConstraint(field("c", "string", c().pattern("(a)\\1")), "pattern", "'pattern' is not a valid RE2 regular expression.");
        assertConstraint(field("c", "string", c().pattern("a".repeat(501))), "pattern",
                "'pattern' is not a valid RE2 regular expression.");
        assertConstraint(field("c", "number", c().pattern("x")), "pattern", "'pattern' is only allowed on string and email fields.");
        assertConstraint(field("d", "string", c().format("dd/MM/yyyy")), "format", "'format' is only allowed on date fields.");
        assertConstraint(field("d", "date", c().format("dd/MM/yyyy HH:mm")), "format",
                "Date pattern 'dd/MM/yyyy HH:mm' must not contain time fields.");
        assertConstraint(field("q", "number", c().min(1).defaultValue("0")), "defaultValue",
                "'defaultValue' does not satisfy the field's type and constraints.");
        assertConstraint(field("q", "number", c().defaultValue("abc")), "defaultValue",
                "'defaultValue' does not satisfy the field's type and constraints.");
    }

    @Test
    void a_default_in_the_field_format_is_fine() {
        SchemaField field = ok(schema("KH", field("d", "date", c().format("dd/MM/yyyy").defaultValue("31/01/2024")))).fields().get(0);
        assertThat(field.constraints().format()).isEqualTo("dd/MM/yyyy");
        assertThat(field.constraints().defaultValue()).isEqualTo("31/01/2024");
    }

    @Test
    void a_default_is_not_judged_when_the_type_is_unknown() {
        assertThat(problems(schema("KH", field("q", "foo", c().defaultValue("x")))))
                .extracting(ProblemItem::code).containsExactly("FIELD_TYPE_INVALID");
    }

    @Test
    void pointers_can_start_inside_a_bigger_body() {
        Checked<DataSchema> checked = SchemaDefinition.check(schema("KH", f("a", "nope")), "/schema");
        assertThat(((Checked.Invalid<DataSchema>) checked).problems().get(0).pointer()).isEqualTo("/schema/fields/0/type");
    }

    private static void assertConstraint(SchemaFieldSpec field, String key, String message) {
        assertThat(only(schema("KH", field))).isEqualTo(
                new ProblemItem(field.name(), "CONSTRAINT_INVALID", message, "/fields/0/constraints/" + key));
    }

    private static void assertProblem(SchemaSpec spec, String code, String pointer) {
        ProblemItem problem = only(spec);
        assertThat(problem.code()).isEqualTo(code);
        assertThat(problem.pointer()).isEqualTo(pointer);
    }

    private static ProblemItem only(SchemaSpec spec) {
        List<ProblemItem> problems = problems(spec);
        assertThat(problems).hasSize(1);
        return problems.get(0);
    }

    private static List<ProblemItem> problems(SchemaSpec spec) {
        Checked<DataSchema> checked = SchemaDefinition.check(spec);
        assertThat(checked).isInstanceOf(Checked.Invalid.class);
        return ((Checked.Invalid<DataSchema>) checked).problems();
    }

    private static DataSchema ok(SchemaSpec spec) {
        Checked<DataSchema> checked = SchemaDefinition.check(spec);
        assertThat(checked).isInstanceOf(Checked.Ok.class);
        return ((Checked.Ok<DataSchema>) checked).value();
    }

    private static SchemaSpec schema(String name, SchemaFieldSpec... fields) {
        return new SchemaSpec(name, List.of(fields));
    }

    private static SchemaFieldSpec f(String name, String type) {
        return new SchemaFieldSpec(name, type, null, null);
    }

    private static SchemaFieldSpec field(String name, String type, C constraints) {
        return new SchemaFieldSpec(name, type, null, constraints.build());
    }

    private static C c() {
        return new C();
    }

    /** Builds constraints one at a time, so a case shows only what it is about. */
    private static final class C {
        private BigDecimal min;
        private BigDecimal max;
        private Integer minLength;
        private Integer maxLength;
        private String pattern;
        private String format;
        private String defaultValue;

        C min(int v) { min = BigDecimal.valueOf(v); return this; }
        C max(int v) { max = BigDecimal.valueOf(v); return this; }
        C minLength(int v) { minLength = v; return this; }
        C maxLength(int v) { maxLength = v; return this; }
        C pattern(String v) { pattern = v; return this; }
        C format(String v) { format = v; return this; }
        C defaultValue(String v) { defaultValue = v; return this; }

        FieldConstraintsSpec build() {
            return new FieldConstraintsSpec(null, min, max, minLength, maxLength, pattern, format, defaultValue);
        }
    }
}
