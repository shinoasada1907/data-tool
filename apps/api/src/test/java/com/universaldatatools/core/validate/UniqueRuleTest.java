package com.universaldatatools.core.validate;

import com.universaldatatools.core.schema.FieldType;
import com.universaldatatools.core.common.RowErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class UniqueRuleTest {

    private final UniqueIndex tracker = new UniqueIndex(UniqueScope.VALID_ROWS);
    private final UniqueRule rule = new UniqueRule();

    @Test
    void a_value_seen_in_a_valid_row_is_a_duplicate() {
        assertThat(validate("a@x.com", 2)).isEqualTo(new ValidationResult.Valid("a@x.com"));
        tracker.commitRow();

        assertThat(validate("a@x.com", 3)).isEqualTo(duplicateOf(2));
    }

    @Test
    void a_value_from_a_row_that_failed_does_not_count() {
        validate("a@x.com", 2);
        tracker.discardRow();

        assertThat(validate("a@x.com", 3)).isEqualTo(new ValidationResult.Valid("a@x.com"));
    }

    @Test
    void numbers_are_compared_by_value() {
        validate(new BigDecimal("1.0"), 2);
        tracker.commitRow();

        assertThat(validate(new BigDecimal("1.00"), 3)).isEqualTo(duplicateOf(2));
        tracker.discardRow();
        assertThat(validate(new BigDecimal("1"), 4)).isEqualTo(duplicateOf(2));
        assertThat(rule.type()).isEqualTo("unique");
    }

    /** Validates one value as the whole of row {@code row}, leaving the row open for commit or discard. */
    private ValidationResult validate(Object value, int row) {
        tracker.beginRow(row);
        return rule.validate(value, new ValidationContext("code", FieldType.NUMBER, row, tracker));
    }

    private static ValidationResult duplicateOf(int row) {
        return new ValidationResult.Invalid(RowErrorCode.VALIDATION_UNIQUE, "Duplicate value; first seen in row " + row + ".");
    }
}
