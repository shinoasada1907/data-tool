package com.universaldatatools.core.schema;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import com.universaldatatools.core.common.ProblemItem;
import com.universaldatatools.core.common.TextValues;
import com.universaldatatools.core.transform.DatePatterns;
import com.universaldatatools.core.validate.FieldRulePlan;
import com.universaldatatools.core.validate.FieldRuleRunner;
import com.universaldatatools.core.validate.UniqueIndex;
import com.universaldatatools.core.validate.UniqueScope;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The rules a schema definition must follow (core-03 SR2). Every problem is reported, not only the first, each with
 * a JSON Pointer to the wrong value, so a form can mark every wrong input at once.
 */
public final class SchemaDefinition {

    public static final int MAX_NAME_LENGTH = 200;
    public static final int MAX_FIELDS = 500;
    public static final int MAX_FIELD_NAME_LENGTH = 100;
    public static final int MAX_TEXT_LENGTH = 32_767;
    public static final int MAX_PATTERN_LENGTH = 500;

    private static final String CONSTRAINT_INVALID = "CONSTRAINT_INVALID";
    private static final FieldConstraintsSpec NO_CONSTRAINTS =
            new FieldConstraintsSpec(null, null, null, null, null, null, null, null);

    private SchemaDefinition() {
    }

    public static Checked<DataSchema> check(SchemaSpec spec) {
        return check(spec, "");
    }

    /** @param prefix pointer to the schema inside the request body, such as {@code /schema}; {@code ""} at the root */
    public static Checked<DataSchema> check(SchemaSpec spec, String prefix) {
        List<ProblemItem> problems = new ArrayList<>();
        String name = TextValues.strip(spec.name());
        if (name == null || name.isEmpty() || length(name) > MAX_NAME_LENGTH) {
            problems.add(new ProblemItem(null, "SCHEMA_NAME_INVALID", "Schema name must be 1-200 characters.",
                    prefix + "/name"));
        }
        List<SchemaFieldSpec> specs = spec.fields() == null ? List.of() : spec.fields();
        if (specs.isEmpty() || specs.size() > MAX_FIELDS) {
            problems.add(new ProblemItem(null, "SCHEMA_FIELDS_INVALID", "Schema must have 1-500 fields.",
                    prefix + "/fields"));
            return new Checked.Invalid<>(problems);
        }
        List<SchemaField> fields = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < specs.size(); i++) {
            SchemaFieldSpec field = specs.get(i) == null ? new SchemaFieldSpec(null, null, null, null) : specs.get(i);
            new FieldCheck(field, prefix + "/fields/" + i, problems, seen).run().ifPresent(fields::add);
        }
        return problems.isEmpty() ? new Checked.Ok<>(new DataSchema(name, fields)) : new Checked.Invalid<>(problems);
    }

    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    /** The checks of one field, in the order of the SR2 table. */
    private static final class FieldCheck {

        private final SchemaFieldSpec spec;
        private final FieldConstraintsSpec c;
        private final String pointer;
        private final List<ProblemItem> problems;
        private final Set<String> seen;
        private String name;

        FieldCheck(SchemaFieldSpec spec, String pointer, List<ProblemItem> problems, Set<String> seen) {
            this.spec = spec;
            this.c = spec.constraints() == null ? NO_CONSTRAINTS : spec.constraints();
            this.pointer = pointer;
            this.problems = problems;
            this.seen = seen;
        }

        Optional<SchemaField> run() {
            int before = problems.size();
            name = TextValues.strip(spec.name());
            if (name == null || name.isEmpty()) {
                name = null;
                add("FIELD_NAME_INVALID", "Field name must not be blank.", "/name");
            } else if (length(name) > MAX_FIELD_NAME_LENGTH) {
                add("FIELD_NAME_INVALID", "Field name must be at most 100 characters.", "/name");
            } else if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                add("FIELD_NAME_DUPLICATE", "Duplicate field name.", "/name");
            }
            Optional<FieldType> type = spec.type() == null ? Optional.empty() : FieldType.fromCode(spec.type());
            if (type.isEmpty()) {
                // Constraints depend on the type, so nothing more can be said about them.
                add("FIELD_TYPE_INVALID", "Unknown field type.", "/type");
                return Optional.empty();
            }
            checkRange(type.get());
            checkLengths(type.get());
            checkPattern(type.get());
            checkFormat(type.get());
            if (problems.size() > before) {
                return Optional.empty();
            }
            SchemaField field = new SchemaField(name, type.get(), Boolean.TRUE.equals(spec.required()),
                    new FieldConstraints(Boolean.TRUE.equals(c.unique()), c.min(), c.max(), c.minLength(),
                            c.maxLength(), c.pattern(), c.format(), c.defaultValue()));
            if (c.defaultValue() != null && !satisfies(field, c.defaultValue())) {
                constraint("'defaultValue' does not satisfy the field's type and constraints.", "defaultValue");
                return Optional.empty();
            }
            return Optional.of(field);
        }

        private void checkRange(FieldType type) {
            boolean number = type == FieldType.NUMBER;
            if (c.min() != null && !number) {
                constraint("'min' is only allowed on number fields.", "min");
            }
            if (c.max() != null && !number) {
                constraint("'max' is only allowed on number fields.", "max");
            }
            if (number && c.min() != null && c.max() != null && c.min().compareTo(c.max()) > 0) {
                constraint("'max' must not be less than 'min'.", "max");
            }
        }

        private void checkLengths(FieldType type) {
            boolean minOk = lengthOk(type, c.minLength(), "minLength");
            boolean maxOk = lengthOk(type, c.maxLength(), "maxLength");
            if (minOk && maxOk && c.minLength() != null && c.maxLength() != null && c.minLength() > c.maxLength()) {
                constraint("'maxLength' must not be less than 'minLength'.", "maxLength");
            }
        }

        private boolean lengthOk(FieldType type, Integer value, String key) {
            if (value == null) {
                return true;
            }
            if (!isText(type)) {
                constraint("'" + key + "' is only allowed on string and email fields.", key);
                return false;
            }
            if (value < 0 || value > MAX_TEXT_LENGTH) {
                constraint("'" + key + "' must be between 0 and 32767.", key);
                return false;
            }
            return true;
        }

        private void checkPattern(FieldType type) {
            if (c.pattern() == null) {
                return;
            }
            if (!isText(type)) {
                constraint("'pattern' is only allowed on string and email fields.", "pattern");
            } else if (c.pattern().isEmpty() || c.pattern().length() > MAX_PATTERN_LENGTH || !compiles(c.pattern())) {
                constraint("'pattern' is not a valid RE2 regular expression.", "pattern");
            }
        }

        private void checkFormat(FieldType type) {
            if (c.format() == null) {
                return;
            }
            if (type != FieldType.DATE) {
                constraint("'format' is only allowed on date fields.", "format");
                return;
            }
            // Dates are read with it, and they are days, not date-times.
            DatePatterns.checkInput(c.format()).or(() -> DatePatterns.checkOutput(c.format()))
                    .ifPresent(message -> constraint(message, "format"));
        }

        private static boolean isText(FieldType type) {
            return type == FieldType.STRING || type == FieldType.EMAIL;
        }

        private static boolean compiles(String pattern) {
            try {
                Pattern.compile(pattern);
                return true;
            } catch (PatternSyntaxException e) {
                return false;
            }
        }

        /** The default must pass every rule of the field but {@code unique}, which says nothing about one value. */
        private static boolean satisfies(SchemaField field, String value) {
            SchemaField alone = new SchemaField(field.name(), field.type(), field.required(),
                    field.constraints().withoutUnique());
            UniqueIndex unused = new UniqueIndex(UniqueScope.ALL_ROWS);
            unused.beginRow(1);
            return !new FieldRuleRunner().validate(FieldRulePlan.of(alone, Set.of()), value, 1, unused).failed();
        }

        private void constraint(String message, String key) {
            add(CONSTRAINT_INVALID, message, "/constraints/" + key);
        }

        private void add(String code, String message, String path) {
            problems.add(new ProblemItem(name, code, message, pointer + path));
        }
    }
}
