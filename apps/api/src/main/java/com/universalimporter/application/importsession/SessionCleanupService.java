package com.universalimporter.application.importsession;

import com.universalimporter.application.common.SessionLocks;
import com.universalimporter.domain.importsession.FileStorage;
import com.universalimporter.domain.importsession.ImportSessionRepository;
import com.universalimporter.domain.importsession.InstallationRepository;
import com.universalimporter.domain.importsession.StoredEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.FileSystemException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deletes what nobody will come back for (design D12, BE-F11 D1 and D2, reworked after review): sessions not
 * changed for longer than the TTL, and session directories no session owns.
 * <ul>
 *   <li>The row goes first, only if unchanged since the cutoff (one step in the database), then the files. A
 *       session is never seen without its files; files that could not go are an orphan, collected later.</li>
 *   <li>Orphans are only looked for in a storage folder this database has claimed; a folder claimed by another
 *       database, or full of directories this one does not know, is left alone.</li>
 *   <li>Too many orphans at once stops the sweep: that looks like the wrong database, not like leftovers.</li>
 * </ul>
 */
@Service
public class SessionCleanupService {

    /** Sessions handled per run; the next run carries on. */
    static final int BATCH = 500;
    /** An upload may take a while between creating its directory and saving its row. */
    static final Duration MINIMUM_ORPHAN_AGE = Duration.ofHours(1);
    /** Above this many orphans, and more than half the directories, nothing is swept. */
    static final int ORPHAN_BREAKER = 10;

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupService.class);

    private final ImportSessionRepository sessions;
    private final FileStorage storage;
    private final InstallationRepository installation;
    private final SessionLocks locks;
    private final CleanupProperties properties;
    private final Clock clock;

    public SessionCleanupService(ImportSessionRepository sessions, FileStorage storage,
                                 InstallationRepository installation, SessionLocks locks,
                                 CleanupProperties properties, Clock clock) {
        this.sessions = sessions;
        this.storage = storage;
        this.installation = installation;
        this.locks = locks;
        this.properties = properties;
        this.clock = clock;
    }

    public CleanupReport cleanupExpired() {
        return cleanupExpired(clock.instant());
    }

    public CleanupReport cleanupExpired(Instant now) {
        Run run = new Run();
        boolean ownsStorage = claimStorage(run);
        Instant cutoff = now.minus(properties.sessionTtl());
        for (UUID id : sessions.findIdsUpdatedBefore(cutoff, BATCH)) {
            deleteExpired(id, cutoff, run);
        }
        if (ownsStorage) {
            Duration orphanAge = properties.sessionTtl().compareTo(MINIMUM_ORPHAN_AGE) < 0
                    ? MINIMUM_ORPHAN_AGE : properties.sessionTtl();
            deleteOrphans(now.minus(orphanAge), run);
        }
        return new CleanupReport(run.deletedSessions, run.deletedOrphans, run.skipped, run.failures);
    }

    /** Whether orphans may be looked for: the storage is this database's, or can safely be claimed now. */
    private boolean claimStorage(Run run) {
        UUID self = installation.installationId();
        Optional<UUID> owner = storage.owner();
        if (owner.isPresent()) {
            if (owner.get().equals(self)) {
                return true;
            }
            run.failures++;
            log.error("The storage folder belongs to installation {}, not to this database ({}); orphan cleanup is "
                    + "off. Give each database its own IMPORTER_STORAGE_DIR.", owner.get(), self);
            return false;
        }
        List<StoredEntry> entries = storage.listEntries();
        if (entries.isEmpty() || entries.stream().anyMatch(entry -> sessions.existsById(entry.sessionId()))) {
            storage.claim(self);
            log.info("Storage folder claimed by installation {}", self);
            return true;
        }
        run.failures++;
        log.error("The storage folder holds {} session directories this database does not know and no owner mark; "
                + "orphan cleanup is off. Give each database its own IMPORTER_STORAGE_DIR.", entries.size());
        return false;
    }

    /** Never waits for a busy session. */
    private void deleteExpired(UUID id, Instant cutoff, Run run) {
        try {
            boolean ran = locks.tryRun(id, () -> {
                if (sessions.deleteIfNotUpdatedSince(id, cutoff)) {
                    run.deletedSessions++;
                    deleteFiles(id, run);
                }
            });
            if (!ran) {
                run.skipped++;
            }
        } catch (RuntimeException e) {
            run.failures++;
            log.warn("Cleanup failed for session {}: {}", id, describe(e));
        }
    }

    /** Files that cannot go now are an orphan, collected by a later run. */
    private void deleteFiles(UUID id, Run run) {
        try {
            storage.delete(id);
        } catch (RuntimeException e) {
            run.failures++;
            run.failedThisRun.add(id);
            log.warn("Files of deleted session {} left for a later run: {}", id, describe(e));
        }
    }

    private void deleteOrphans(Instant cutoff, Run run) {
        List<StoredEntry> entries = storage.listEntries();
        List<UUID> orphans = entries.stream()
                .filter(entry -> entry.lastModified().isBefore(cutoff))
                .map(StoredEntry::sessionId)
                .filter(id -> !run.failedThisRun.contains(id))
                .filter(id -> !sessions.existsById(id))
                .toList();
        if (orphans.size() > ORPHAN_BREAKER && orphans.size() * 2 > entries.size()) {
            run.failures++;
            log.error("{} of {} session directories have no session; that looks like the wrong database, so no "
                    + "orphan is deleted this run", orphans.size(), entries.size());
            return;
        }
        for (UUID id : orphans) {
            try {
                storage.delete(id);
                run.deletedOrphans++;
            } catch (RuntimeException e) {
                run.failures++;
                log.warn("Cleanup failed for orphan directory {}: {}", id, describe(e));
            }
        }
    }

    /** The failure and the file that caused it: paths hold only the storage folder, session ids and fixed names. */
    private static String describe(RuntimeException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        String file = cause instanceof FileSystemException failure && failure.getFile() != null
                ? " on " + failure.getFile() : "";
        return cause.getClass().getName() + file;
    }

    private static final class Run {
        private int deletedSessions;
        private int deletedOrphans;
        private int skipped;
        private int failures;
        private final Set<UUID> failedThisRun = new HashSet<>();
    }
}
