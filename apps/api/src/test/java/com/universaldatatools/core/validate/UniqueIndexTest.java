package com.universaldatatools.core.validate;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** core-03 task 6; the VALID_ROWS cases are those of V0.1's UniqueTrackerTest. */
class UniqueIndexTest {

    private final UniqueIndex valid = new UniqueIndex(UniqueScope.VALID_ROWS);
    private final UniqueIndex all = new UniqueIndex(UniqueScope.ALL_ROWS);

    @Test
    void a_committed_value_is_remembered_with_its_row() {
        see(valid, 2, "email", "a@x.com");
        valid.commitRow();
        assertThat(firstRow(valid, "email", "a@x.com")).isEqualTo(2);
    }

    @Test
    void a_discarded_value_is_forgotten() {
        see(valid, 2, "email", "a@x.com");
        valid.discardRow();
        assertThat(firstRow(valid, "email", "a@x.com")).isNull();
    }

    @Test
    void numbers_compare_by_value() {
        see(valid, 2, "code", new BigDecimal("1.0"));
        valid.commitRow();
        assertThat(firstRow(valid, "code", new BigDecimal("1.00"))).isEqualTo(2);
        assertThat(firstRow(valid, "code", new BigDecimal("1"))).isEqualTo(2);
        assertThat(firstRow(valid, "code", new BigDecimal("10"))).isNull();
        see(valid, 3, "zero", new BigDecimal("0.00"));
        valid.commitRow();
        assertThat(firstRow(valid, "zero", BigDecimal.ZERO)).isEqualTo(3);
    }

    @Test
    void the_number_one_is_not_the_text_one() {
        see(valid, 2, "code", BigDecimal.ONE);
        valid.commitRow();
        assertThat(firstRow(valid, "code", "1")).isNull();
    }

    @Test
    void fields_are_independent() {
        see(valid, 2, "email", "x");
        valid.commitRow();
        assertThat(firstRow(valid, "code", "x")).isNull();
    }

    @Test
    void strings_are_case_sensitive_dates_compare_by_day() {
        see(valid, 2, "email", "Abc");
        see(valid, 2, "day", LocalDate.of(2024, 1, 31));
        valid.commitRow();
        assertThat(firstRow(valid, "email", "abc")).isNull();
        assertThat(firstRow(valid, "day", LocalDate.parse("2024-01-31"))).isEqualTo(2);
    }

    @Test
    void the_first_committed_row_wins() {
        valid.beginRow(2);
        valid.commitRow();
        see(valid, 3, "email", "b");
        valid.commitRow();
        see(valid, 4, "email", "b");
        valid.commitRow();
        assertThat(firstRow(valid, "email", "b")).isEqualTo(3);
    }

    @Test
    void a_row_must_be_committed_or_discarded_before_the_next_begins() {
        see(valid, 2, "email", "a@x.com");
        assertThatThrownBy(() -> valid.beginRow(3)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void values_can_only_be_recorded_inside_a_row() {
        assertThatThrownBy(() -> valid.record(valid.key("email", "a"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(valid::commitRow).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> all.record(all.key("email", "a"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void all_rows_counts_a_value_even_from_a_row_that_failed() {
        see(all, 2, "email", "a@x.com");
        all.discardRow();
        assertThat(firstRow(all, "email", "a@x.com")).isEqualTo(2);
    }

    @Test
    void no_false_duplicates_among_many_values() {
        for (int i = 0; i < 300_000; i++) {
            all.beginRow(i + 2);
            assertThat(all.firstRowOf(all.key("id", "v" + i))).isEmpty();
            all.record(all.key("id", "v" + i));
            all.commitRow();
        }
    }

    private static void see(UniqueIndex index, int row, String field, Object value) {
        try {
            index.beginRow(row);
        } catch (IllegalStateException sameRow) {
            // A second field of the row that is already open.
        }
        index.record(index.key(field, value));
    }

    private static Integer firstRow(UniqueIndex index, String field, Object value) {
        var row = index.firstRowOf(index.key(field, value));
        return row.isPresent() ? row.getAsInt() : null;
    }
}
