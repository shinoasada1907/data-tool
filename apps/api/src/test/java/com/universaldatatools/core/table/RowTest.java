package com.universaldatatools.core.table;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RowTest {

    @Test
    void value_by_index_is_null_past_the_last_cell() {
        Row row = new Row(2, List.of("a"));

        assertThat(row.value(0)).isEqualTo("a");
        assertThat(row.value(3)).isNull();
    }

    @Test
    void keeps_null_cells_and_is_not_affected_by_later_changes_to_the_source_list() {
        List<String> source = new ArrayList<>(Arrays.asList("x", null));
        Row row = new Row(2, source);

        source.set(0, "changed");

        assertThat(row.values()).containsExactly("x", null);
    }

    @Test
    void kind_comes_from_the_source_when_it_has_types() {
        assertThat(new Row(2, List.of("a"), CellKinds.of(List.of(CellKind.TEXT))).kind(0)).isEqualTo(CellKind.TEXT);
        assertThat(new Row(2, List.of("a")).kind(0)).isNull();
    }
}
