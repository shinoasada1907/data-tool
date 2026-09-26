package com.universalimporter.application.common;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Runs the writes on one session one after another (design D11, S5). Wrap the whole transaction, so the lock is
 * released only after the commit and the next write reads committed data. One JVM only: V0.1 runs one instance.
 */
@Component
public class SessionLocks {

    /** Never shrinks until F11 cleans up sessions; each entry is a few dozen bytes. */
    private final Map<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withLock(UUID sessionId, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(sessionId, id -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
