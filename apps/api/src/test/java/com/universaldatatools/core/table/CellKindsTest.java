package com.universaldatatools.core.table;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.universaldatatools.core.table.CellKind.NUMBER;
import static com.universaldatatools.core.table.CellKind.TEXT;
import static org.assertj.core.api.Assertions.assertThat;

class CellKindsTest {

    @Test
    void kind_by_index_is_null_for_an_empty_cell_and_past_the_end() {
        CellKinds kinds = CellKinds.of(Arrays.asList(TEXT, null, NUMBER));

        assertThat(kinds.get(0)).isEqualTo(TEXT);
        assertThat(kinds.get(1)).isNull();
        assertThat(kinds.get(2)).isEqualTo(NUMBER);
        assertThat(kinds.get(5)).isNull();
    }

    @Test
    void equal_content_is_equal() {
        assertThat(CellKinds.of(List.of(TEXT, NUMBER))).isEqualTo(CellKinds.of(List.of(TEXT, NUMBER)))
                .hasSameHashCodeAs(CellKinds.of(List.of(TEXT, NUMBER)));
    }
}
