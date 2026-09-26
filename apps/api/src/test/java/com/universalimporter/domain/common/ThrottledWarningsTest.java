package com.universalimporter.domain.common;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

class ThrottledWarningsTest {

    private final List<String> logged = new ArrayList<>();
    private final ThrottledWarnings warnings = new ThrottledWarnings(new CapturingLogger());

    @Test
    void the_same_failure_is_logged_at_its_1st_10th_and_100th_occurrence() {
        for (int i = 0; i < 100; i++) {
            warnings.warn("trim", () -> "trim broke");
        }

        assertThat(logged).containsExactly("trim broke", "trim broke (10 occurrences so far)",
                "trim broke (100 occurrences so far)");
    }

    @Test
    void keys_are_counted_separately() {
        warnings.warn("trim", () -> "trim broke");
        warnings.warn("unique", () -> "unique broke");

        assertThat(logged).containsExactly("trim broke", "unique broke");
    }

    private final class CapturingLogger implements System.Logger {

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public boolean isLoggable(Level level) {
            return true;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
            logged.add(msg);
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
            logged.add(format);
        }
    }
}
