package com.universalimporter.domain.validation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.assertj.core.api.Assertions.assertThat;

class UniqueTrackerTest {

    private final UniqueTracker tracker = new UniqueTracker();

    @Test
    void a_committed_value_is_remembered_with_its_row() {
        tracker.beginRow(2);
        tracker.stage("email", "a@x.com");
        tracker.commitRow();

        assertThat(tracker.firstRowOf("email", "a@x.com")).contains(2);
    }

    @Test
    void a_discarded_value_is_forgotten() {
        tracker.beginRow(2);
        tracker.stage("email", "a@x.com");
        tracker.discardRow();

        assertThat(tracker.firstRowOf("email", "a@x.com")).isEmpty();
    }

    @Test
    void numbers_compare_by_value() {
        tracker.beginRow(2);
        tracker.stage("code", UniqueTracker.canonical(new BigDecimal("1.0")));
        tracker.commitRow();

        assertThat(tracker.firstRowOf("code", UniqueTracker.canonical(new BigDecimal("1.00")))).contains(2);
        assertThat(tracker.firstRowOf("code", UniqueTracker.canonical(new BigDecimal("1")))).contains(2);
        assertThat(tracker.firstRowOf("code", UniqueTracker.canonical(new BigDecimal("10")))).isEmpty();
    }

    @Test
    void fields_are_independent() {
        tracker.beginRow(2);
        tracker.stage("email", "x");
        tracker.commitRow();

        assertThat(tracker.firstRowOf("code", "x")).isEmpty();
    }

    @Test
    void strings_are_case_sensitive() {
        tracker.beginRow(2);
        tracker.stage("email", "A@x.com");
        tracker.commitRow();

        assertThat(tracker.firstRowOf("email", "a@x.com")).isEmpty();
    }

    @Test
    void the_first_committed_row_wins() {
        tracker.beginRow(2);
        tracker.commitRow();
        tracker.beginRow(3);
        tracker.stage("email", "b");
        tracker.commitRow();
        tracker.beginRow(4);
        tracker.stage("email", "b");
        tracker.commitRow();

        assertThat(tracker.firstRowOf("email", "b")).contains(3);
    }

    @Test
    void a_row_must_be_committed_or_discarded_before_the_next_begins() {
        tracker.beginRow(2);
        tracker.stage("email", "a@x.com");

        assertThatThrownBy(() -> tracker.beginRow(3)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void values_can_only_be_staged_inside_a_row() {
        assertThatThrownBy(() -> tracker.stage("email", "a@x.com")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tracker.commitRow()).isInstanceOf(IllegalStateException.class);
    }
}
