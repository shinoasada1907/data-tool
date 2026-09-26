package com.universalimporter.infrastructure.scheduling;

import com.universalimporter.application.importsession.CleanupReport;
import com.universalimporter.application.importsession.SessionCleanupService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionCleanupSchedulerTest {

    private final SessionCleanupService service = mock(SessionCleanupService.class);
    private final SessionCleanupScheduler scheduler = new SessionCleanupScheduler(service);

    @Test
    void a_run_cleans_up_once() {
        when(service.cleanupExpired()).thenReturn(new CleanupReport(2, 1, 0, 0));

        scheduler.run();

        verify(service, times(1)).cleanupExpired();
    }

    @Test
    void a_failing_run_never_kills_the_scheduler_thread() {
        when(service.cleanupExpired()).thenThrow(new RuntimeException("db down"));

        assertThatCode(scheduler::run).doesNotThrowAnyException();
    }
}
