package com.universalimporter.application.importsession;

import com.universalimporter.application.common.SessionLocks;
import com.universalimporter.domain.importsession.ImportSession;
import com.universalimporter.domain.importsession.SourceFile;
import com.universalimporter.domain.importsession.SourceFileType;
import com.universalimporter.support.InMemoryFileStorage;
import com.universalimporter.support.InMemoryImportSessionRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCleanupServiceTest {

    private static final Instant T0 = Instant.parse("2026-09-25T12:00:00Z");
    private static final SourceFile FILE = new SourceFile("customers.csv", SourceFileType.CSV, 3);
    private static final UUID A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    private InMemoryImportSessionRepository sessions = new InMemoryImportSessionRepository();
    private final InMemoryFileStorage storage = new InMemoryFileStorage();
    private final SessionLocks locks = new SessionLocks();

    private SessionCleanupService service() {
        return new SessionCleanupService(sessions, storage, locks,
                new CleanupProperties(true, Duration.ofHours(24), Duration.ofHours(1)), Clock.fixed(T0, ZoneOffset.UTC));
    }

    @Test
    void an_expired_session_goes_with_its_files_and_a_live_one_stays() {
        givenSession(A, hoursAgo(25));
        givenSession(B, hoursAgo(23));

        CleanupReport report = service().cleanupExpired(T0);

        assertThat(report).isEqualTo(new CleanupReport(1, 0, 0, 0));
        assertThat(sessions.existsById(A)).isFalse();
        assertThat(storage.holds(A)).isFalse();
        assertThat(sessions.existsById(B)).isTrue();
        assertThat(storage.holds(B)).isTrue();
    }

    @Test
    void the_session_stays_when_its_files_cannot_be_deleted() {
        givenSession(A, hoursAgo(25));
        givenSession(C, hoursAgo(26));
        storage.failDeleteFor(A);

        CleanupReport report = service().cleanupExpired(T0);

        assertThat(report).isEqualTo(new CleanupReport(1, 0, 0, 1));
        assertThat(sessions.existsById(A)).isTrue();
        assertThat(sessions.existsById(C)).isFalse();
        assertThat(storage.holds(C)).isFalse();
    }

    @Test
    void a_session_in_use_is_skipped_until_the_next_run() throws Exception {
        givenSession(A, hoursAgo(25));
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService other = Executors.newSingleThreadExecutor()) {
            other.submit(() -> locks.withLock(A, () -> {
                held.countDown();
                awaitQuietly(release);
                return null;
            }));
            try {
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();

                CleanupReport report = service().cleanupExpired(T0);

                assertThat(report).isEqualTo(new CleanupReport(0, 0, 1, 0));
                assertThat(sessions.existsById(A)).isTrue();
            } finally {
                release.countDown();
            }
        }
        assertThat(service().cleanupExpired(T0)).isEqualTo(new CleanupReport(1, 0, 0, 0));
    }

    @Test
    void a_database_failure_is_counted_and_the_run_goes_on() {
        givenSession(A, hoursAgo(25));
        givenSession(C, hoursAgo(26));
        sessions.failDeleteFor(A);

        CleanupReport report = service().cleanupExpired(T0);

        assertThat(report).isEqualTo(new CleanupReport(1, 0, 0, 1));
        assertThat(sessions.existsById(C)).isFalse();
    }

    @Test
    void a_session_changed_after_it_was_listed_is_kept() {
        givenSession(A, hoursAgo(1));
        InMemoryImportSessionRepository live = sessions;
        sessions = new InMemoryImportSessionRepository() {
            @Override
            public List<UUID> findIdsUpdatedBefore(Instant cutoff, int limit) {
                return List.of(A); // listed while it was still expired
            }
        };
        sessions.save(live.findById(A).orElseThrow());

        CleanupReport report = service().cleanupExpired(T0);

        assertThat(report.deletedSessions()).isZero();
        assertThat(sessions.existsById(A)).isTrue();
        assertThat(storage.holds(A)).isTrue();
    }

    @Test
    void an_old_directory_without_a_session_is_an_orphan() {
        UUID orphan = UUID.randomUUID();
        storage.directory(orphan, hoursAgo(25));

        CleanupReport report = service().cleanupExpired(T0);

        assertThat(report).isEqualTo(new CleanupReport(0, 1, 0, 0));
        assertThat(storage.holds(orphan)).isFalse();
    }

    @Test
    void a_recent_directory_without_a_session_may_be_an_upload_in_progress() {
        UUID uploading = UUID.randomUUID();
        storage.directory(uploading, hoursAgo(1));

        assertThat(service().cleanupExpired(T0)).isEqualTo(new CleanupReport(0, 0, 0, 0));
        assertThat(storage.holds(uploading)).isTrue();
    }

    @Test
    void an_old_directory_of_a_live_session_is_not_an_orphan() {
        givenSession(B, hoursAgo(23));
        storage.directory(B, T0.minus(Duration.ofDays(30)));

        assertThat(service().cleanupExpired(T0)).isEqualTo(new CleanupReport(0, 0, 0, 0));
        assertThat(storage.holds(B)).isTrue();
    }

    @Test
    void nothing_to_clean_is_an_empty_report() {
        assertThat(service().cleanupExpired(T0)).isEqualTo(new CleanupReport(0, 0, 0, 0));
    }

    @Test
    void one_run_deletes_at_most_500_sessions() {
        for (int i = 0; i < 600; i++) {
            givenSession(UUID.randomUUID(), T0.minus(Duration.ofHours(25)).minusSeconds(i));
        }

        CleanupReport report = service().cleanupExpired(T0);

        assertThat(report.deletedSessions()).isEqualTo(500);
        assertThat(sessions.findIdsUpdatedBefore(T0, 1000)).hasSize(100);
    }

    @Test
    void without_an_argument_it_uses_the_clock() {
        givenSession(A, hoursAgo(25));

        assertThat(service().cleanupExpired().deletedSessions()).isEqualTo(1);
    }

    private void givenSession(UUID id, Instant lastChanged) {
        sessions.save(ImportSession.create(id, FILE, lastChanged));
        storage.save(id, new ByteArrayInputStream("a,b".getBytes()));
        storage.directory(id, lastChanged);
    }

    private static Instant hoursAgo(int hours) {
        return T0.minus(Duration.ofHours(hours));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
