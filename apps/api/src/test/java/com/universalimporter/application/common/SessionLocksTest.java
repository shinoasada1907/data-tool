package com.universalimporter.application.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionLocksTest {

    private static final UUID A = UUID.fromString("0b6f0c52-8a8e-4d5c-9a55-2f3c1c3f7e11");
    private static final UUID B = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final SessionLocks locks = new SessionLocks();

    @Test
    void actions_on_the_same_session_never_overlap() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<long[]> first = pool.submit(() -> locks.withLock(A, SessionLocksTest::timedSleep));
            Future<long[]> second = pool.submit(() -> locks.withLock(A, SessionLocksTest::timedSleep));
            List<long[]> spans = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));

            long[] earlier = spans.get(0)[0] <= spans.get(1)[0] ? spans.get(0) : spans.get(1);
            long[] later = earlier == spans.get(0) ? spans.get(1) : spans.get(0);
            assertThat(later[0]).isGreaterThanOrEqualTo(earlier[1]);
        }
    }

    @Test
    void actions_on_different_sessions_run_side_by_side() throws Exception {
        assertThat(SessionLocks.stripe(A)).as("A and B must not share a lock").isNotEqualTo(SessionLocks.stripe(B));
        // Each action waits for the other to start: only possible if neither blocks the other.
        CountDownLatch bothStarted = new CountDownLatch(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Boolean> onA = pool.submit(() -> locks.withLock(A, () -> meet(bothStarted)));
            Future<Boolean> onB = pool.submit(() -> locks.withLock(B, () -> meet(bothStarted)));

            assertThat(onA.get(2, TimeUnit.SECONDS)).isTrue();
            assertThat(onB.get(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void a_failing_action_rethrows_and_releases_the_lock() throws Exception {
        IllegalStateException failure = new IllegalStateException("boom");

        assertThatThrownBy(() -> locks.withLock(A, () -> {
            throw failure;
        })).isSameAs(failure);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            assertThat(pool.submit(() -> locks.withLock(A, () -> "next")).get(2, TimeUnit.SECONDS)).isEqualTo("next");
        }
    }

    @Test
    void try_run_runs_when_the_session_is_free() {
        boolean[] ran = {false};

        assertThat(locks.tryRun(A, () -> ran[0] = true)).isTrue();
        assertThat(ran[0]).isTrue();
    }

    @Test
    void try_run_gives_up_at_once_when_another_thread_holds_the_session() throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        boolean[] ran = {false};
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            pool.submit(() -> locks.withLock(A, () -> {
                held.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            try {
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();

                assertThat(locks.tryRun(A, () -> ran[0] = true)).isFalse();
                assertThat(ran[0]).isFalse();
            } finally {
                release.countDown();
            }
        }
        assertThat(locks.tryRun(A, () -> ran[0] = true)).isTrue();
    }

    @Test
    void try_run_releases_the_lock_when_the_action_fails() throws Exception {
        assertThatThrownBy(() -> locks.tryRun(A, () -> {
            throw new IllegalStateException("boom");
        })).hasMessage("boom");

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            assertThat(pool.submit(() -> locks.tryRun(A, () -> { })).get(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void the_lock_is_reentrant() {
        assertThat(locks.withLock(A, () -> locks.withLock(A, () -> 1))).isEqualTo(1);
    }

    private static long[] timedSleep() {
        long start = System.nanoTime();
        try {
            Thread.sleep(Duration.ofMillis(100));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return new long[]{start, System.nanoTime()};
    }

    private static boolean meet(CountDownLatch bothStarted) {
        bothStarted.countDown();
        try {
            return bothStarted.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
