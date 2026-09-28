package com.universaldatatools.tools.importer.domain.mapping;

import com.universaldatatools.core.table.Row;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MappingStrategiesTest {

    private static final Row ROW = new Row(2, Arrays.asList("An", null));

    private final SourceColumnMappingStrategy sourceColumn = new SourceColumnMappingStrategy();
    private final ConstantMappingStrategy constant = new ConstantMappingStrategy();

    @Test
    void a_source_column_mapping_takes_the_cell_at_its_index() {
        assertThat(sourceColumn.map(ROW, column(0))).isEqualTo("An");
    }

    @Test
    void an_empty_cell_stays_null() {
        assertThat(sourceColumn.map(ROW, column(1))).isNull();
    }

    @Test
    void a_cell_missing_from_a_short_row_is_null() {
        assertThat(sourceColumn.map(ROW, column(5))).isNull();
    }

    @Test
    void a_constant_mapping_ignores_the_row() {
        assertThat(constant.map(ROW, new ResolvedMapping("country", MappingType.CONSTANT, -1, "VN"))).isEqualTo("VN");
    }

    @Test
    void the_standard_strategies_cover_every_mapping_type() {
        MappingStrategies strategies = MappingStrategies.standard();

        assertThat(strategies.strategyFor(MappingType.SOURCE_COLUMN)).isInstanceOf(SourceColumnMappingStrategy.class);
        assertThat(strategies.strategyFor(MappingType.CONSTANT)).isInstanceOf(ConstantMappingStrategy.class);
        assertThat(sourceColumn.type()).isEqualTo(MappingType.SOURCE_COLUMN);
        assertThat(constant.type()).isEqualTo(MappingType.CONSTANT);
    }

    private static ResolvedMapping column(int index) {
        return new ResolvedMapping("name", MappingType.SOURCE_COLUMN, index, null);
    }
}
