package com.universaldatatools.tools.importer.domain.mapping;

import com.universaldatatools.core.table.Column;
import com.universaldatatools.core.table.Row;
import com.universaldatatools.core.table.SourceSchema;
import com.universaldatatools.tools.importer.domain.schema.FieldSpec;
import com.universaldatatools.tools.importer.domain.schema.TargetSchema;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RowMapperTest {

    private static final TargetSchema SCHEMA = TargetSchema.define(List.of(
            new FieldSpec("name", "string", true, 0),
            new FieldSpec("country", "string", false, 1),
            new FieldSpec("note", "string", false, 2)));
    private static final SourceSchema SOURCE =
            new SourceSchema(List.of(new Column(0, "x"), new Column(1, "Họ tên")), 2, null);
    private static final MappingConfig MAPPING = MappingConfig.define(List.of(
            new MappingSpec("name", "SOURCE_COLUMN", "Họ tên", null),
            new MappingSpec("country", "CONSTANT", null, "VN")), SCHEMA, SOURCE);

    private final RowMapper mapper = RowMapper.of(SCHEMA, MAPPING, SOURCE, MappingStrategies.standard());

    @Test
    void a_row_becomes_raw_values_in_schema_order() {
        LinkedHashMap<String, String> values = mapper.map(new Row(2, List.of("x", "An")));

        assertThat(values.keySet()).containsExactly("name", "country", "note");
        assertThat(values.values()).containsExactly("An", "VN", null);
    }

    @Test
    void a_cell_missing_from_a_short_row_maps_to_null() {
        LinkedHashMap<String, String> values = mapper.map(new Row(3, List.of("y")));

        assertThat(values.keySet()).containsExactly("name", "country", "note");
        assertThat(values.values()).containsExactly(null, "VN", null);
    }

    @Test
    void the_result_depends_only_on_the_row() {
        Row row = new Row(2, List.of("x", "An"));

        assertThat(mapper.map(row)).isEqualTo(mapper.map(row));
    }
}
