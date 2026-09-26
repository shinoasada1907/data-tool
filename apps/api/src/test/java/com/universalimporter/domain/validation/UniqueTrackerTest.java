package com.universalimporter.domain.validation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class UniqueTrackerTest {

    private final UniqueTracker tracker = new UniqueTracker();

    @Test
    void a_committed_value_is_remembered_with_its_row() {
        tracker.stage("email", "a@x.com");
        tracker.commitRow(2);

        assertThat(tracker.firstRowOf("email", "a@x.com")).contains(2);
    }

    @Test
    void a_discarded_value_is_forgotten() {
        tracker.stage("email", "a@x.com");
        tracker.discardRow();

        assertThat(tracker.firstRowOf("email", "a@x.com")).isEmpty();
    }

    @Test
    void numbers_compare_by_value() {
        tracker.stage("code", UniqueTracker.canonical(new BigDecimal("1.0")));
        tracker.commitRow(2);

        assertThat(tracker.firstRowOf("code", UniqueTracker.canonical(new BigDecimal("1.00")))).contains(2);
        assertThat(tracker.firstRowOf("code", UniqueTracker.canonical(new BigDecimal("1")))).contains(2);
        assertThat(tracker.firstRowOf("code", UniqueTracker.canonical(new BigDecimal("10")))).isEmpty();
    }

    @Test
    void fields_are_independent() {
        tracker.stage("email", "x");
        tracker.commitRow(2);

        assertThat(tracker.firstRowOf("code", "x")).isEmpty();
    }

    @Test
    void strings_are_case_sensitive() {
        tracker.stage("email", "A@x.com");
        tracker.commitRow(2);

        assertThat(tracker.firstRowOf("email", "a@x.com")).isEmpty();
    }

    @Test
    void the_first_committed_row_wins() {
        tracker.commitRow(2);
        tracker.stage("email", "b");
        tracker.commitRow(3);
        tracker.stage("email", "b");
        tracker.commitRow(4);

        assertThat(tracker.firstRowOf("email", "b")).contains(3);
    }
}
