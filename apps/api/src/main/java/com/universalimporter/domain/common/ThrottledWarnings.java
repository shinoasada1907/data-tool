package com.universalimporter.domain.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Logs the same kind of unexpected failure at its 1st, 10th, 100th… occurrence only. A bug that hits every cell of
 * a million-row file then writes a handful of lines, not a million. Keys must come from a small fixed set (a rule or
 * transformation type), never from data.
 */
public final class ThrottledWarnings {

    private final System.Logger log;
    private final Map<String, AtomicLong> counts = new ConcurrentHashMap<>();

    public ThrottledWarnings(System.Logger log) {
        this.log = log;
    }

    public void warn(String key, Supplier<String> message) {
        long occurrence = counts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        if (isPowerOfTen(occurrence)) {
            log.log(System.Logger.Level.WARNING, message.get()
                    + (occurrence > 1 ? " (" + occurrence + " occurrences so far)" : ""));
        }
    }

    private static boolean isPowerOfTen(long n) {
        while (n >= 10 && n % 10 == 0) {
            n /= 10;
        }
        return n == 1;
    }
}
