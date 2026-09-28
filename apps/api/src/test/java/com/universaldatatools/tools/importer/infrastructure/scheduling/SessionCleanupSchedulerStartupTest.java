package com.universaldatatools.tools.importer.infrastructure.scheduling;

import com.universaldatatools.tools.importer.application.importsession.CleanupProperties;
import com.universaldatatools.tools.importer.application.importsession.SessionCleanupService;
import com.universaldatatools.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SessionCleanupSchedulerStartupTest {

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void cleanupOnItsOwnStorage(DynamicPropertyRegistry registry) {
        registry.add("toolbox.storage.dir", storageDir::toString);
        registry.add("toolbox.importer.cleanup.enabled", () -> "true");
    }

    /** Not reset between tests: the call under test happens once, while the context starts. */
    @MockitoSpyBean(reset = MockReset.NONE)
    SessionCleanupService service;

    @Autowired
    CleanupProperties properties;

    @Test
    void the_cleanup_runs_as_soon_as_the_application_starts() {
        verify(service, timeout(5000).atLeastOnce()).cleanupExpired();
    }

    @Test
    void the_defaults_are_a_day_to_live_and_an_hourly_run() {
        assertThat(properties.sessionTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(properties.interval()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.enabled()).isTrue();
    }
}
