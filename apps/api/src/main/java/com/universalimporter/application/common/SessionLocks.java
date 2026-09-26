package com.universalimporter.application.common;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Runs the writes on one session one after another (design D11, S5). Wrap the whole transaction, so the lock is
 * released only after the commit and the next write reads committed data. One JVM only: V0.1 runs one instance.
 * <p>
 * A fixed table of locks, picked by the session id's hash: memory stays bounded even when clients send made-up
 * ids, and nothing has to be removed when a session is deleted. Two sessions that share a lock merely wait for
 * each other. Never hold two session locks at once: that order is not fixed, so it could deadlock.
 */
@Component
public class SessionLocks {

    static final int STRIPES = 1024;

    private final ReentrantLock[] locks = new ReentrantLock[STRIPES];

    public SessionLocks() {
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    public <T> T withLock(UUID sessionId, Supplier<T> action) {
        ReentrantLock lock = locks[stripe(sessionId)];
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    static int stripe(UUID sessionId) {
        return Math.floorMod(sessionId.hashCode(), STRIPES);
    }
}
