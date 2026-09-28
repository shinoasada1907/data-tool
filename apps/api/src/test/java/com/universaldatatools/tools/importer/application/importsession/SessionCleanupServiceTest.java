package com.universaldatatools.tools.importer.application.importsession;

import com.universaldatatools.core.table.DataFormat;
import com.universaldatatools.support.InMemoryFileStorage;
import com.universaldatatools.support.InMemoryImportSessionRepository;
import com.universaldatatools.tools.importer.application.common.SessionLocks;
import com.universaldatatools.tools.importer.domain.importsession.ImportSession;
import com.universaldatatools.tools.importer.domain.importsession.SourceFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class SessionCleanupServiceTest {

    private static final Instant T0 = Instant.parse("2026-09-25T12:00:00Z");
    private static final SourceFile FILE = new SourceFile("customers.csv", DataFormat.CSV, 3);
    private static final UUID INSTALLATION = UUID.fromString("99999999-0000-0000-0000-000000000009");
    private static final UUID A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    private final InMemoryImportSessionRepository sessions = new InMemoryImportSessionRepository();
    private final InMemoryFileStorage storage = new InMemoryFileStorage();
    private final SessionLocks locks = new SessionLocks();

    private SessionCleanupService service() {
        return service(Duration.ofHours(24));
    }

    private SessionCleanupService service(Duration ttl) {
        return new SessionCleanupService(sessions, storage, () -> INSTALLATION, locks,
                new CleanupProperties(true, ttl, Duration.ofHours(1)), Clock.fixed(T0, ZoneOffset.UTC));
    }

    // ---- expired sessions ----

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
    void the_row_goes_first_so_files_that_cannot_be_deleted_become_an_orphan_for_later() {
        givenSession(A, hoursAgo(25));
        givenSession(C, hoursAgo(26));
        storage.failDeleteFor(A);

        CleanupReport first = service().cleanupExpired(T0);

        assertThat(first).isEqualTo(new CleanupReport(2, 0, 0, 1));
        assertThat(sessions.existsById(A)).as("never a session without its files").isFalse();
        assertThat(storage.holds(A)).isTrue();

        storage.allowDeletes();
        assertThat(service().cleanupExpired(T0).deletedOrphans()).isEqualTo(1);
        assertThat(storage.holds(A)).isFalse();
    }

    @Test
    void a_database_failure_leaves_the_session_whole_for_the_next_run() {
        givenSession(A, hoursAgo(25));
        givenSession(C, hoursAgo(26));
        sessions.failDeleteFor(A);

        CleanupReport first = service().cleanupExpired(T0);

        assertThat(first).isEqualTo(new CleanupReport(1, 0, 0, 1));
        assertThat(sessions.existsById(A)).isTrue();
        assertThat(storage.holds(A)).as("files kept with their row").isTrue();
        assertThat(sessions.existsById(C)).isFalse();

        sessions.allowDeletes();
        assertThat(service().cleanupExpired(T0).deletedSessions()).isEqualTo(1);
        assertThat(storage.holds(A)).isFalse();
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
    void a_session_changed_after_it_was_listed_is_kept_with_its_files() {
        givenSession(A, hoursAgo(25));
        sessions.beforeNextConditionalDelete(() -> sessions.save(ImportSession.create(A, FILE, hoursAgo(0))));

        CleanupReport report = service().cleanupExpired(T0);

        assertThat(report.deletedSessions()).isZero();
        assertThat(sessions.existsById(A)).isTrue();
        assertThat(storage.holds(A)).isTrue();
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

    @Test
    void nothing_to_clean_is_an_empty_report() {
        assertThat(service().cleanupExpired(T0)).isEqualTo(new CleanupReport(0, 0, 0, 0));
    }

    // ---- orphans ----

    @Test
    void an_old_directory_without_a_session_is_an_orphan() {
        givenSession(B, hoursAgo(1));
        UUID orphan = UUID.randomUUID();
        storage.directory(orphan, hoursAgo(25));

        CleanupReport report = service().cleanupExpired(T0);

        assertThat(report).isEqualTo(new CleanupReport(0, 1, 0, 0));
        assertThat(storage.holds(orphan)).isFalse();
    }

    @Test
    void a_recent_directory_without_a_session_may_be_an_upload_in_progress() {
        storage.claim(INSTALLATION);
        UUID uploading = UUID.randomUUID();
        storage.directory(uploading, hoursAgo(1));

        assertThat(service().cleanupExpired(T0)).isEqualTo(new CleanupReport(0, 0, 0, 0));
        assertThat(storage.holds(uploading)).isTrue();
    }

    @Test
    void orphans_wait_at_least_an_hour_even_with_a_shorter_ttl() {
        storage.claim(INSTALLATION);
        UUID uploading = UUID.randomUUID();
        storage.directory(uploading, T0.minus(Duration.ofMinutes(30)));

        assertThat(service(Duration.ofMinutes(1)).cleanupExpired(T0).deletedOrphans()).isZero();
        assertThat(storage.holds(uploading)).isTrue();
    }

    @Test
    void an_old_directory_of_a_live_session_is_not_an_orphan() {
        givenSession(B, hoursAgo(23));
        storage.directory(B, T0.minus(Duration.ofDays(30)));

        assertThat(service().cleanupExpired(T0)).isEqualTo(new CleanupReport(0, 0, 0, 0));
        assertThat(storage.holds(B)).isTrue();
    }

    // ---- storage and database belong together ----

    @Test
    void storage_marked_by_another_database_is_never_swept_for_orphans() {
        storage.claim(UUID.randomUUID());
        UUID foreign = UUID.randomUUID();
        storage.directory(foreign, hoursAgo(48));

        CleanupReport report = service().cleanupExpired(T0);

        assertThat(report.deletedOrphans()).isZero();
        assertThat(report.failures()).isEqualTo(1);
        assertThat(storage.holds(foreign)).isTrue();
    }

    @Test
    void unmarked_storage_full_of_directories_this_database_does_not_know_is_left_alone() {
        UUID foreign1 = UUID.randomUUID();
        UUID foreign2 = UUID.randomUUID();
        storage.directory(foreign1, hoursAgo(48));
        storage.directory(foreign2, hoursAgo(48));

        CleanupReport report = service().cleanupExpired(T0);

        assertThat(report.deletedOrphans()).isZero();
        assertThat(storage.holds(foreign1)).isTrue();
        assertThat(storage.owner()).isEmpty();
    }

    @Test
    void unmarked_storage_holding_a_known_session_is_claimed() {
        givenSession(B, hoursAgo(1));
        UUID orphan = UUID.randomUUID();
        storage.directory(orphan, hoursAgo(48));

        assertThat(service().cleanupExpired(T0).deletedOrphans()).isEqualTo(1);
        assertThat(storage.owner()).contains(INSTALLATION);
    }

    @Test
    void empty_storage_is_claimed() {
        service().cleanupExpired(T0);

        assertThat(storage.owner()).contains(INSTALLATION);
    }

    @Test
    void too_many_orphans_at_once_trips_the_breaker() {
        givenSession(B, hoursAgo(1));
        storage.claim(INSTALLATION);
        for (int i = 0; i < 11; i++) {
            storage.directory(UUID.randomUUID(), hoursAgo(48));
        }

        CleanupReport report = service().cleanupExpired(T0);

        assertThat(report.deletedOrphans()).isZero();
        assertThat(report.failures()).isEqualTo(1);
        assertThat(storage.listEntries()).hasSize(12);
    }

    // ---- configuration ----

    @Test
    void a_time_to_live_under_a_minute_is_refused() {
        assertThat(catchThrowableOfType(IllegalArgumentException.class,
                () -> new CleanupProperties(true, Duration.ofMillis(24), Duration.ofHours(1)))).isNotNull();
        assertThat(catchThrowableOfType(IllegalArgumentException.class,
                () -> new CleanupProperties(true, Duration.ofHours(-24), Duration.ofHours(1)))).isNotNull();
        assertThat(catchThrowableOfType(IllegalArgumentException.class,
                () -> new CleanupProperties(true, Duration.ofHours(24), Duration.ZERO))).isNotNull();
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
