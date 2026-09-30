package com.universaldatatools.platform.dataset;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A read/write lock per dataset (core-04 PL5): reads (preview, a tool streaming the file) share it; deleting takes
 * it alone, so a file is never removed under a reader. Locks nobody holds are dropped.
 */
@Component
public class DatasetLocks {

    private final ConcurrentHashMap<UUID, Entry> locks = new ConcurrentHashMap<>();

    /** A held lock; releasing it twice is harmless. */
    public interface Held extends AutoCloseable {
        @Override
        void close();
    }

    public Held read(UUID id) {
        Entry entry = acquire(id);
        entry.lock.readLock().lock();
        return release(id, entry, entry.lock.readLock());
    }

    /** The write lock if it comes within {@code wait}, else empty. */
    public Optional<Held> tryWrite(UUID id, Duration wait) {
        Entry entry = acquire(id);
        boolean locked;
        try {
            locked = entry.lock.writeLock().tryLock(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            locked = false;
        }
        if (!locked) {
            forget(id, entry);
            return Optional.empty();
        }
        return Optional.of(release(id, entry, entry.lock.writeLock()));
    }

    int size() {
        return locks.size();
    }

    private Entry acquire(UUID id) {
        return locks.compute(id, (key, entry) -> {
            Entry held = entry == null ? new Entry() : entry;
            held.users++;
            return held;
        });
    }

    private Held release(UUID id, Entry entry, Lock lock) {
        return new Held() {
            private boolean released;

            @Override
            public synchronized void close() {
                if (!released) {
                    released = true;
                    lock.unlock();
                    forget(id, entry);
                }
            }
        };
    }

    private void forget(UUID id, Entry entry) {
        locks.computeIfPresent(id, (key, current) -> {
            if (current != entry) {
                return current;
            }
            current.users--;
            return current.users == 0 ? null : current;
        });
    }

    private static final class Entry {

        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        /** Holders and waiters; changed only inside {@link ConcurrentHashMap#compute}. */
        private int users;
    }
}
