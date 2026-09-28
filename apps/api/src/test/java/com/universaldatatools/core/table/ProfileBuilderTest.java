package com.universaldatatools.core.table;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** core-02 IO7 (spec: dataset-io, "Profile của cột"). */
class ProfileBuilderTest {

    @Test
    void untyped_text_is_inferred_cautiously() {
        assertThat(untyped("1", "2", "-3.5")).isEqualTo(new ColumnProfile(InferredType.NUMBER, 0, 4));
        assertThat(untyped("00123", "00456").inferredType()).isEqualTo(InferredType.STRING);
        assertThat(untyped("true", "FALSE", null)).isEqualTo(new ColumnProfile(InferredType.BOOLEAN, 1, 5));
        assertThat(untyped("2024-01-31", "2024-02-30").inferredType()).isEqualTo(InferredType.STRING);
        assertThat(untyped("an@x.com", "  ")).isEqualTo(new ColumnProfile(InferredType.EMAIL, 1, 8));
        assertThat(untyped("1234567890123456").inferredType()).isEqualTo(InferredType.STRING);
        assertThat(untyped("1", "0").inferredType()).isEqualTo(InferredType.NUMBER);
        assertThat(untyped(null, " ")).isEqualTo(new ColumnProfile(InferredType.EMPTY, 2, 1));
        assertThat(untyped("Nguyễn").maxLength()).isEqualTo(6);
        assertThat(untyped("😀😀").maxLength()).isEqualTo(2);
    }

    @Test
    void typed_cells_are_profiled_by_kind() {
        assertThat(typed(cell("12.5", CellKind.NUMBER), cell("1e3", CellKind.NUMBER)))
                .isEqualTo(InferredType.NUMBER);
        assertThat(typed(cell("1", CellKind.NUMBER), cell("x", CellKind.TEXT))).isEqualTo(InferredType.STRING);
        assertThat(typed(cell("2024-01-01", CellKind.DATE))).isEqualTo(InferredType.DATE);
        assertThat(typed(cell("a@x.com", CellKind.TEXT), cell("b@y.vn", CellKind.TEXT)))
                .isEqualTo(InferredType.EMAIL);
        assertThat(typed(cell("123", CellKind.TEXT))).as("text never becomes a number").isEqualTo(InferredType.STRING);
        assertThat(typed(cell("true", CellKind.BOOLEAN))).isEqualTo(InferredType.BOOLEAN);
    }

    private static ColumnProfile untyped(String... cells) {
        ProfileBuilder builder = new ProfileBuilder(1);
        Arrays.stream(cells).forEach(text -> builder.accept(0, text, null));
        return builder.build().getFirst();
    }

    private record Cell(String text, CellKind kind) {
    }

    private static Cell cell(String text, CellKind kind) {
        return new Cell(text, kind);
    }

    private static InferredType typed(Cell... cells) {
        ProfileBuilder builder = new ProfileBuilder(1);
        List.of(cells).forEach(cell -> builder.accept(0, cell.text(), cell.kind()));
        return builder.build().getFirst().inferredType();
    }
}
