package com.universaldatatools.tools.validator;

import com.universaldatatools.core.schema.DataSchema;
import com.universaldatatools.core.schema.FieldConstraints;
import com.universaldatatools.core.schema.FieldType;
import com.universaldatatools.core.schema.SchemaField;
import com.universaldatatools.tools.validator.application.ColumnMatcher;
import com.universaldatatools.tools.validator.application.ValidatorRecords.Matched;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** tool-05 VD3. */
class ColumnMatcherTest {

    @Test
    void same_name_first_then_loose_match() {
        ColumnMatcher.Match match = ColumnMatcher.match(schema(field("email", true), field("Email", false)),
                List.of(" EMAIL ", "Email"));
        assertThat(match.columnIndexes()).containsExactly(0, 1);
        assertThat(match.compatibility().matched())
                .containsExactly(new Matched("email", " EMAIL "), new Matched("Email", "Email"));
    }

    @Test
    void a_column_serves_one_field() {
        ColumnMatcher.Match match = ColumnMatcher.match(schema(field("name", false), field("NAME", false)),
                List.of("Name"));
        assertThat(match.columnIndexes()).containsExactly(0, -1);
        assertThat(match.compatibility().missingOptional()).containsExactly("NAME");
    }

    @Test
    void missing_required_fields_and_extra_columns() {
        ColumnMatcher.Match match = ColumnMatcher.match(
                schema(field("phone", true), field("id", true), field("note", false)), List.of("id", "x", "y"));
        assertThat(match.missingRequired()).extracting(p -> p.field() + ":" + p.code())
                .containsExactly("phone:FIELD_MISSING");
        assertThat(match.compatibility().missingOptional()).containsExactly("note");
        assertThat(match.compatibility().extraColumns()).containsExactly("x", "y");
    }

    private static DataSchema schema(SchemaField... fields) {
        return new DataSchema("S", List.of(fields));
    }

    private static SchemaField field(String name, boolean required) {
        return new SchemaField(name, FieldType.STRING, required, FieldConstraints.NONE);
    }
}
