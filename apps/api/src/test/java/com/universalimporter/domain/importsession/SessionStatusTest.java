package com.universalimporter.domain.importsession;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The full lifecycle table from design D2: every (from, to) pair is listed explicitly. */
class SessionStatusTest {

    @ParameterizedTest(name = "{0} → {1}: {2}")
    @CsvSource({
            "UPLOADED,    UPLOADED,    false",
            "UPLOADED,    CONFIGURING, true",
            "UPLOADED,    READY,       false",
            "UPLOADED,    PROCESSED,   false",
            "UPLOADED,    FAILED,      false",
            "CONFIGURING, UPLOADED,    false",
            "CONFIGURING, CONFIGURING, true",
            "CONFIGURING, READY,       true",
            "CONFIGURING, PROCESSED,   false",
            "CONFIGURING, FAILED,      false",
            "READY,       UPLOADED,    false",
            "READY,       CONFIGURING, true",
            "READY,       READY,       true",
            "READY,       PROCESSED,   true",
            "READY,       FAILED,      true",
            "PROCESSED,   UPLOADED,    false",
            "PROCESSED,   CONFIGURING, true",
            "PROCESSED,   READY,       true",
            "PROCESSED,   PROCESSED,   true",
            "PROCESSED,   FAILED,      true",
            "FAILED,      UPLOADED,    false",
            "FAILED,      CONFIGURING, false",
            "FAILED,      READY,       false",
            "FAILED,      PROCESSED,   false",
            "FAILED,      FAILED,      false"
    })
    void follows_the_lifecycle_table(SessionStatus from, SessionStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
