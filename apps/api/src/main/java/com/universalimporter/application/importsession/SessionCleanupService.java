package com.universalimporter.application.importsession;

import com.universalimporter.application.common.SessionLocks;
import com.universalimporter.domain.importsession.FileStorage;
import com.universalimporter.domain.importsession.ImportSessionRepository;
import com.universalimporter.domain.importsession.StoredEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Deletes what nobody will come back for (design D12, BE-F11 D1 and D2): sessions not changed for longer than
 * the TTL, with their files, and session directories the database no longer knows. Files go first, then the row:
 * a row without files is found and retried next run, files without a row become an orphan and go later.
 */
@Service
public class SessionCleanupService {

    /** Sessions handled per run; the next run carries on. */
    static final int BATCH = 500;

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupService.class);

    private final ImportSessionRepository sessions;
    private final FileStorage storage;
    private final SessionLocks locks;
    private final CleanupProperties properties;
    private final Clock clock;

    public SessionCleanupService(ImportSessionRepository sessions, FileStorage storage, SessionLocks locks,
                                 CleanupProperties properties, Clock clock) {
        this.sessions = sessions;
        this.storage = storage;
        this.locks = locks;
        this.properties = properties;
        this.clock = clock;
    }

    public CleanupReport cleanupExpired() {
        return cleanupExpired(clock.instant());
    }

    public CleanupReport cleanupExpired(Instant now) {
        Instant cutoff = now.minus(properties.sessionTtl());
        Counts counts = new Counts();
        for (UUID id : sessions.findIdsUpdatedBefore(cutoff, BATCH)) {
            deleteExpired(id, cutoff, counts);
        }
        for (StoredEntry entry : storage.listEntries()) {
            if (entry.lastModified().isBefore(cutoff) && !sessions.existsById(entry.sessionId())) {
                deleteOrphan(entry.sessionId(), counts);
            }
        }
        return new CleanupReport(counts.deletedSessions, counts.deletedOrphans, counts.skipped, counts.failures);
    }

    /** Never waits for a busy session, and checks again under the lock: it may have changed since it was listed. */
    private void deleteExpired(UUID id, Instant cutoff, Counts counts) {
        try {
            boolean ran = locks.tryRun(id, () -> {
                boolean stillExpired = sessions.findById(id)
                        .map(session -> session.updatedAt().isBefore(cutoff))
                        .orElse(false);
                if (stillExpired) {
                    storage.delete(id);
                    sessions.deleteById(id);
                    counts.deletedSessions++;
                }
            });
            if (!ran) {
                counts.skipped++;
            }
        } catch (RuntimeException e) {
            counts.failures++;
            log.warn("Cleanup failed for session {}: {}", id, e.getClass().getName());
        }
    }

    private void deleteOrphan(UUID id, Counts counts) {
        try {
            storage.delete(id);
            counts.deletedOrphans++;
        } catch (RuntimeException e) {
            counts.failures++;
            log.warn("Cleanup failed for orphan directory {}: {}", id, e.getClass().getName());
        }
    }

    private static final class Counts {
        private int deletedSessions;
        private int deletedOrphans;
        private int skipped;
        private int failures;
    }
}
